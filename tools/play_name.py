#!/usr/bin/env python3
"""
Название приложения для версии Google Play.
Пример: python3 tools/play_name.py "Стрілка" "Стрілка" "Strilka"   (ru, uk, en)

Создаёт app/src/play/res/values*/strings.xml: там переопределены все строки, где упоминается
название (у GitHub-версии остаётся «Авторадио»), и подставляет название в PRIVACY.md и store/*.
"""
import os, re, sys

ru, uk, en = sys.argv[1:4]
root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
old = {'values-ru': ('Авторадио', ru), 'values-uk': ('Авторадіо', uk), 'values': ('Car Radio', en)}
for folder, (was, now) in old.items():
    src = open(f'{root}/app/src/main/res/{folder}/strings.xml', encoding='utf-8').read()
    lines = [l for l in src.split('\n') if '<string ' in l and (was in l or 'name="app_name"' in l)]
    out = ['<?xml version="1.0" encoding="utf-8"?>', '<!-- Сгенерировано tools/play_name.py: название для Google Play -->', '<resources>']
    out += [l.replace(was, now) for l in lines]
    out.append('</resources>')
    dst = f'{root}/app/src/play/res/{folder}'
    os.makedirs(dst, exist_ok=True)
    open(f'{dst}/strings.xml', 'w', encoding='utf-8').write('\n'.join(out) + '\n')
    print(folder, len(lines), 'strings')

names = {'en-US': en, 'uk': uk, 'ru-RU': ru}
for lang, name in names.items():
    for f in ('short.txt', 'full.txt'):
        p = f'{root}/store/{lang}/{f}'
        s = open(p, encoding='utf-8').read().replace('{NAME}', name)
        open(p, 'w', encoding='utf-8').write(s)
    open(f'{root}/store/{lang}/title.txt', 'w', encoding='utf-8').write(name + '\n')
p = f'{root}/PRIVACY.md'
s = open(p, encoding='utf-8').read()
s = s.replace('# {NAME}', f'# {en}')
s = s.split('## English', 1)
s = s[0] + '## English' + s[1].replace('{NAME}', en, 3)
s = s.replace('## Українська\n\n{NAME}', f'## Українська\n\n{uk}').replace('## Русский\n\n{NAME}', f'## Русский\n\n{ru}')
s = s.replace('{NAME}', en)
open(p, 'w', encoding='utf-8').write(s)
print('ok')
