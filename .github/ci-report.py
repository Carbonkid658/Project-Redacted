"""Turns CI output into check-run annotations so build errors and API lookups can be read
through the GitHub API (the raw log download is not always reachable)."""
import glob, json, os, re, subprocess, sys, urllib.request, zipfile

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
            for nxt in lines[i + 1:i + 4]:
                if nxt.strip().startswith(('symbol', 'location', '>', 'required', 'found', 'reason')):
                    keep.append('    ' + nxt.strip())
    emit('Errors', '\n'.join(keep) or '\n'.join(lines[-150:]), 'error')
elif mode == 'probe':
    queries = [q.strip() for q in open('.github/probe.txt') if q.strip() and not q.startswith('#')]
    if not queries:
        sys.exit(0)
    home = os.path.expanduser('~/.gradle/caches')
    jars = [j for j in glob.glob(home + '/**/*.jar', recursive=True)
            if re.search(r'minecraft|fabric', j) and 'sources' not in j]
    index = {}
    for j in jars:
        try:
            with zipfile.ZipFile(j) as z:
                for n in z.namelist():
                    if n.endswith('.class') and '$' not in n:
                        index.setdefault(n[:-6].replace('/', '.'), j)
        except Exception:
            pass
    cp = ':'.join(sorted(set(jars)))
    out = []
    for q in queries:
        kind, _, arg = q.partition(':')
        if kind == 'find':
            hits = sorted(c for c in index if re.search(arg, c))
            out.append(f'## find {arg}\n' + '\n'.join(hits[:200]))
        elif kind == 'class':
            r = subprocess.run(['javap', '-public', '-cp', cp, arg], capture_output=True, text=True)
            out.append(f'## {arg}\n' + (r.stdout or r.stderr))
    emit('Probe', '\n\n'.join(out))
