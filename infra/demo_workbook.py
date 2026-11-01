"""
Turns the league's real workbook into a fictional one for the public demo: same structure, results, dates and rules,
but every team and every player gets an invented name. The demo then looks like a real season without showing anyone.

    infra/.venv/Scripts/python.exe infra/demo_workbook.py "design/TORNEO CLAUSURA 2026-7.xlsx" "design/DEMO 2026.xlsx"

Needs openpyxl in the infra virtualenv: infra/.venv/Scripts/python.exe -m pip install openpyxl==3.1.5
Both workbooks stay in design/, which git ignores. ExcelImportApiTests checks the result loads like the real one.

- The same real name always gets the same invented one, in every sheet, so tables, fixtures and scorers still match.
- Team names inside free text ("Nota: X se dio de baja") are replaced too.
- It fails if any real team or player name is left anywhere in the output.
- Values only: formulas are written as their last computed value (the importer reads values).
"""
import random
import re
import sys
import warnings

import openpyxl

warnings.filterwarnings('ignore', module='openpyxl')

TEAM_SHEETS = {  # sheet -> columns with team names (below their header)
    'Tabla Clausura': 'BQ', 'Tabla Apertura ': 'BQ',
    'Part Jug': 'DG', 'Prox Partido': 'DG',
    'Goleadores': 'C', 'Figuras': 'C', 'Imbatibles': 'C', 'Sancionados': 'C',
    'HISTORIA': 'CGKOSW',
}
PLAYER_SHEETS = {'Goleadores': 'B', 'Figuras': 'B', 'Imbatibles': 'B', 'Sancionados': 'B'}
FIRST_DATA_ROW = 8  # headers are on rows 7-9; titles above
HEADERS = {'EQUIPO', 'JUGADOR', 'EQUIPO CAMPEON', 'AÑO', 'POSICIÓN', 'GOLES', 'TOTAL', 'TOTALES'}
NOT_TEAMS = re.compile(r'(?i)^(libre|control|total(es)?|s\.?n\.?|proximamente!*|equipo.*|jugador|-|\d.*|.*\bcat\b.*|'
                       r'ganador.*|perdedor.*|.*°.*|partido suspendido|'
                       # the date of each round sits in a team column ("Domingo 20 de Septiembre")
                       r'(lunes|martes|mi[eé]rcoles|jueves|viernes|s[aá]bado|domingo)\b.*)$')
PLAYER = re.compile(r"^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ'´` .-]+,\s*[A-Za-zÁÉÍÓÚÜÑáéíóúüñ'´` .-]+$")

TEAM_WORDS_A = ['DEPORTIVO', 'ATLÉTICO', 'SPORTING', 'UNIÓN', 'REAL', 'JUVENTUD', 'ESTRELLA', 'RACING', 'SOCIAL',
                'CENTRAL', 'FERRO', 'DEFENSORES DE']
TEAM_WORDS_B = ['CEIBO', 'TALA', 'OMBÚ', 'LAPACHO', 'ALGARROBO', 'ESPINILLO', 'QUEBRACHO', 'TIPA', 'CHAÑAR', 'SAUCE',
                'ARAUCARIA', 'JACARANDÁ', 'PALO BORRACHO', 'AROMO', 'CALDÉN', 'MISTOL', 'TIMBÓ', 'CINA CINA']
TEAM_SOLO = ['LOS HORNEROS', 'LOS TEROS', 'LOS CARANCHOS', 'LAS CALANDRIAS', 'LOS ZORZALES', 'EL BENTEVEO',
             'LOS CARPINCHOS', 'LOS YACARÉS', 'LOS PUMAS DEL OESTE', 'LA TROPILLA', 'EL REJUNTE', 'LOS DE SIEMPRE',
             'LA VIEJA GUARDIA', 'LOS PIBES', 'EL ACOPLADO', 'LA BARRA DEL 98', 'LOS INMORTALES', 'EL FERROCARRIL']
FIRST = ['Martín', 'Lucas', 'Matías', 'Nicolás', 'Juan', 'Tomás', 'Santiago', 'Facundo', 'Agustín', 'Federico',
         'Gonzalo', 'Diego', 'Pablo', 'Ezequiel', 'Leandro', 'Franco', 'Joaquín', 'Ignacio', 'Rodrigo', 'Hernán',
         'Maximiliano', 'Sebastián', 'Emiliano', 'Damián', 'Cristian', 'Lautaro', 'Bruno', 'Mariano', 'Germán', 'Ariel']
LAST = ['Ferreyra', 'Acosta', 'Benítez', 'Medina', 'Herrera', 'Aguirre', 'Giménez', 'Molina', 'Sosa', 'Castro',
        'Ortiz', 'Rojas', 'Núñez', 'Luna', 'Cabrera', 'Ríos', 'Peralta', 'Godoy', 'Vega', 'Correa', 'Quiroga',
        'Ledesma', 'Villalba', 'Paz', 'Arias', 'Figueroa', 'Toledo', 'Maidana', 'Bustos', 'Coronel', 'Ojeda',
        'Domínguez', 'Ponce', 'Vera', 'Carrizo', 'Moyano', 'Ibarra', 'Funes', 'Olmos', 'Barrios']


def norm(v):
    return re.sub(r'\s+', ' ', str(v)).strip().upper()


def column_cells(ws, letters):
    for letter in letters:
        for (cell,) in ws.iter_rows(min_row=FIRST_DATA_ROW, min_col=openpyxl.utils.column_index_from_string(letter),
                                    max_col=openpyxl.utils.column_index_from_string(letter)):
            if isinstance(cell.value, str) and cell.value.strip():
                yield cell


def is_team(value):
    v = norm(value)
    # "BOCHA FC/LA CANTERA" (a player who changed teams) is two teams: each part is replaced inside the text
    return (len(v) >= 2 and v not in HEADERS and not NOT_TEAMS.match(v) and ',' not in v and '/' not in v
            and not v.startswith('NOTA'))


def invented_teams(rng, n, avoid):
    pool = TEAM_SOLO + [f'{a} {b}' for a in TEAM_WORDS_A for b in TEAM_WORDS_B]
    rng.shuffle(pool)
    names = [p for p in pool if norm(p) not in avoid]
    if len(names) < n:
        raise SystemExit(f'not enough invented team names ({len(names)} for {n})')
    return names[:n]


def invented_players(rng, n, avoid):
    combos = [f'{last}, {first}' for last in LAST for first in FIRST]
    rng.shuffle(combos)
    names = [c for c in combos if norm(c) not in avoid]
    return names[:n]


def main(src, dst):
    wb = openpyxl.load_workbook(src, data_only=True)
    rng = random.Random(2026)  # same input -> same invented names (a reset gives the same demo)

    real_teams = sorted({norm(c.value) for name, cols in TEAM_SHEETS.items() if name in wb.sheetnames
                         for c in column_cells(wb[name], cols) if is_team(c.value)})
    real_players = sorted({norm(c.value) for name, cols in PLAYER_SHEETS.items() if name in wb.sheetnames
                           for c in column_cells(wb[name], cols) if PLAYER.match(c.value.strip())})
    teams = dict(zip(real_teams, invented_teams(rng, len(real_teams), set(real_teams))))
    players = dict(zip(real_players, invented_players(rng, len(real_players), set(real_players))))
    print(f'{len(teams)} teams and {len(players)} players renamed')

    # Longest first, so "LA BANDA DEL SUR" is replaced before "LA BANDA"
    team_re = re.compile(r'(?i)(?<![\wÁÉÍÓÚÑ])(' + '|'.join(re.escape(t) for t in sorted(teams, key=len, reverse=True))
                         + r')(?![\wÁÉÍÓÚÑ])')
    for ws in wb.worksheets:
        for row in ws.iter_rows():
            for cell in row:
                if not isinstance(cell.value, str):
                    continue
                key = norm(cell.value)
                if key in players:
                    cell.value = players[key]
                elif key in teams:
                    cell.value = teams[key]
                elif team_re.search(cell.value):
                    cell.value = team_re.sub(lambda m: teams[norm(m.group(1))], cell.value)

    # Nothing real may be left: every string cell is checked against every real name
    leaks = []
    real_words = [(t, re.compile(r'(?i)(?<![\wÁÉÍÓÚÑ])' + re.escape(t) + r'(?![\wÁÉÍÓÚÑ])')) for t in teams]
    real_words += [(p, re.compile(re.escape(p), re.I)) for p in players]
    invented = {norm(v) for v in list(teams.values()) + list(players.values())}
    for ws in wb.worksheets:
        for row in ws.iter_rows():
            for cell in row:
                if isinstance(cell.value, str) and norm(cell.value) not in invented:
                    for real, rx in real_words:
                        if rx.search(cell.value):
                            leaks.append(f'{ws.title}!{cell.coordinate}')
                            break
    if leaks:
        raise SystemExit(f'real names left in {len(leaks)} cells, e.g. {leaks[:5]}')

    wb.save(dst)
    print(f'written {dst}')


if __name__ == '__main__':
    if len(sys.argv) != 3:
        raise SystemExit(__doc__)
    main(sys.argv[1], sys.argv[2])
