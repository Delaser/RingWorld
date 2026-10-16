"""Pure validation of native Atlas UI acknowledgement/render boundaries."""
import re

EXPECTED = {
    'atlas-ui-04-running': 'RUNNING',
    'atlas-ui-07-paused': 'PAUSED',
    'atlas-ui-08-resumed': 'RUNNING',
    'atlas-ui-09-cancelled': 'CANCELLED',
}
PATTERN = re.compile(r'\[atlas-ui-test\] rendered-state capture=([\w-]+) state=(\w+) acknowledgedFrame=(\d+) captureFrame=(\d+)')


def validate(log):
    rows = []
    for match in PATTERN.finditer(log):
        name, state, acknowledged, captured = match.groups()
        acknowledged, captured = int(acknowledged), int(captured)
        if name not in EXPECTED or state != EXPECTED[name] or captured - acknowledged < 3:
            raise ValueError('Atlas UI capture lacks the expected rendered acknowledgement')
        if rows and (captured <= rows[-1]['captureFrame'] or acknowledged < rows[-1]['captureFrame']):
            raise ValueError('Atlas UI rendered capture boundaries are not ordered')
        rows.append(dict(capture=name, state=state, acknowledgedFrame=acknowledged, captureFrame=captured))
    if [r['capture'] for r in rows] != list(EXPECTED):
        raise ValueError('Missing, duplicate or reordered Atlas UI rendered-state proof')
    return dict(status='PASS', kind='ACKNOWLEDGED_RENDERED_ATLAS_UI_STATES', records=rows,
                limits='Native acknowledgement and actual frame delay; screenshot content still requires visual inspection.')
