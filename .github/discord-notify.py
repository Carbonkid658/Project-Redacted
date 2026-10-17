"""Posts a finished main build to Discord: build number, the pushed commits and the mod jar."""
import glob, json, os, subprocess, sys

webhook = os.environ.get('DISCORD_WEBHOOK', '')
if not webhook:
    print('DISCORD_WEBHOOK secret not set; skipping')
    sys.exit(0)

event = json.load(open(os.environ['GITHUB_EVENT_PATH']))
lines = []
for c in event.get('commits', [])[-10:]:
    title = c['message'].splitlines()[0][:100].replace('[', '(').replace(']', ')')
    who = (c.get('author') or {}).get('username') or (c.get('author') or {}).get('name', '?')
    lines.append(f"> ✨ [{title}](<{c['url']}>), by {who}")
if len(event.get('commits', [])) > 10:
    lines.insert(0, f"> …and {len(event['commits']) - 10} earlier commits")
content = f"New `Project Redacted` dev build `#{os.environ['GITHUB_RUN_NUMBER']}`:\n" + '\n'.join(lines)

jars = [j for j in glob.glob('build/libs/*.jar') if not j.endswith('-sources.jar')]
cmd = ['curl', '-sS', '--fail-with-body', '-F', 'payload_json=' + json.dumps({'content': content[:2000]})]
if jars:
    cmd += ['-F', f'files[0]=@{jars[0]}']
cmd.append(webhook)
sys.exit(subprocess.run(cmd).returncode)
