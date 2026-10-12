"""Publishes compiler errors from a failed build as a check run on the commit, so they can be read
through the GitHub API without downloading the raw log."""
import json, os, re, sys, urllib.request

def emit(title, text, level='notice'):
    """Publishes text as extra check runs on the commit (annotations are capped at 4 KB)."""
    chunks = [text[i:i + 60000] for i in range(0, len(text), 60000)] or ['(empty)']
    for n, c in enumerate(chunks[:20]):
        body = json.dumps({
            'name': f'report: {title} {n + 1}/{len(chunks)}',
            'head_sha': os.environ['GITHUB_SHA'],
            'status': 'completed',
            'conclusion': 'neutral',
            'output': {'title': title, 'summary': f'part {n + 1}', 'text': c},
        }).encode()
        req = urllib.request.Request(
            f"{os.environ['GITHUB_API_URL']}/repos/{os.environ['GITHUB_REPOSITORY']}/check-runs",
            data=body, method='POST', headers={
                'Authorization': f"Bearer {os.environ['GITHUB_TOKEN']}",
                'Accept': 'application/vnd.github+json'})
        urllib.request.urlopen(req).read()

mode = sys.argv[1]
if mode == 'errors':
    lines = open('build.log', errors='replace').read().splitlines()
    keep = []
    for i, l in enumerate(lines):
        if re.search(r': error:|: warning: \[removal\]|What went wrong|Could not |FAILURE', l):
            keep.append(l.replace(os.getcwd() + '/', ''))
            if 'What went wrong' in l:
                keep.extend('    ' + x for x in lines[i + 1:i + 15])
            for nxt in lines[i + 1:i + 4]:
                if nxt.strip().startswith(('symbol', 'location', '>', 'required', 'found', 'reason')):
                    keep.append('    ' + nxt.strip())
    emit('Errors', '\n'.join(keep + ['', '--- log tail ---'] + lines[-80:]), 'error')
elif mode == 'smoke':
    log = open('smoke.log', errors='replace').read()
    emit('Smoke', log[-120000:])
elif mode == 'probe':
    # Looks up method signatures of the classes listed in .github/probe.txt, to check API names after
    # Minecraft or Fabric renames. Lines: find:<regex> (class names) or class:<fqcn>[:<line regex>].
    import glob, subprocess, zipfile
    queries = [q.strip() for q in open('.github/probe.txt') if q.strip() and not q.startswith('#')]
    if not queries:
        sys.exit(0)
    jars = [j for j in glob.glob(os.path.expanduser('~/.gradle/caches') + '/**/*.jar', recursive=True)
            if re.search(r'minecraft|fabric|joml', j) and 'sources' not in j]
    index = set()
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                index.update(n[:-6].replace('/', '.') for n in z.namelist() if n.endswith('.class'))
        except Exception:
            pass
    cp = ':'.join(sorted(set(jars)))
    out = []
    for q in queries:
        kind, _, arg = q.partition(':')
        if kind == 'find':
            out.append(f'## find {arg}\n' + '\n'.join(sorted(c for c in index if re.search(arg, c))[:300]))
        elif kind == 'class':
            name, _, pattern = arg.partition(':')
            r = subprocess.run(['javap', '-protected', '-cp', cp, name], capture_output=True, text=True)
            text = r.stdout or r.stderr
            if pattern:
                text = '\n'.join(l for l in text.splitlines() if re.search(pattern, l))
            out.append(f'## {name}\n' + text)
        elif kind == 'code':
            # code:<fqcn>:<regex> prints the bytecode of methods whose header or body matches
            name, _, pattern = arg.partition(':')
            r = subprocess.run(['javap', '-c', '-p', '-cp', cp, name], capture_output=True, text=True)
            blocks, cur = [], []
            for l in (r.stdout or r.stderr).splitlines():
                if l.startswith('  ') and not l.startswith('   ') and cur:
                    blocks.append(cur); cur = []
                cur.append(l)
            blocks.append(cur)
            hits = ['\n'.join(b[:400]) for b in blocks if re.search(pattern, '\n'.join(b))]
            out.append(f'## code {name} /{pattern}/\n' + '\n\n'.join(hits[:12]))
    emit('Probe', '\n\n'.join(out))
