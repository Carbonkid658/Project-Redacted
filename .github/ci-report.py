"""Turns CI output into check-run annotations so build errors and API lookups can be read
through the GitHub API (the raw log download is not always reachable)."""
import glob, os, re, subprocess, sys, zipfile

def emit(title, text, level='notice'):
    chunks = [text[i:i + 60000] for i in range(0, len(text), 60000)] or ['(empty)']
    for n, c in enumerate(chunks[:9]):
        enc = c.replace('%', '%25').replace('\r', '%0D').replace('\n', '%0A')
        print(f'::{level} title={title} {n + 1}/{len(chunks)}::{enc}')

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
