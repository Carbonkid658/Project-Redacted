"""Generates the deco blocks: DecoBlocks.java, blockstates, models, item models, textures, lang, loot tables,
stonecutter recipes and tags. Run from the repo root:  python3 tools/deco/gen.py

The block list lives in specs.py and the texture painters in paint.py. Generated textures are placeholders;
textures are only drawn with --textures (and then only missing ones); put your own PNGs in textures/block/deco/
under the names the models use.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(__file__))
from PIL import Image  # noqa: E402

import paint as P  # noqa: E402
from specs import B, MATS  # noqa: E402
from tex import T, col  # noqa: E402

NS = 'projectredacted'
ROOT = os.getcwd()
RES = os.path.join(ROOT, 'src/main/resources')
A = os.path.join(RES, 'assets', NS)
D = os.path.join(RES, 'data', NS)
MC = os.path.join(RES, 'data/minecraft/tags')
TEX_DIR = os.path.join(A, 'textures/block/deco')
ITEM_TEX_DIR = os.path.join(A, 'textures/item/deco')
JAVA = os.path.join(ROOT, 'src/main/java/dev/projectredacted/registry/DecoBlocks.java')
FORCE_TEX = '--textures' in sys.argv

written_tex = set()


def dump(path, obj):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w') as f:
        json.dump(obj, f, indent=2)
        f.write('\n')


def tex_ref(name):
    return f'{NS}:block/deco/{name}'


def paint_tex(name, painter):
    if name in written_tex:
        return
    written_tex.add(name)
    path = os.path.join(TEX_DIR, name + '.png')
    if os.path.exists(path) or not FORCE_TEX:
        return
    os.makedirs(TEX_DIR, exist_ok=True)
    t = T(name)
    painter(t)
    t.save(path)


def mat_tex(m):
    """Texture name for a material used by a box: a MATS key or an (r, g, b) colour."""
    if isinstance(m, tuple):
        name = 'mat_c_%d_%d_%d' % m
        paint_tex(name, P.panel(m))
        return name
    paint_tex('mat_' + m, MATS[m])
    return 'mat_' + m


def model(name, obj):
    dump(os.path.join(A, 'models/block', name + '.json'), obj)


def blockstate(name, obj):
    dump(os.path.join(A, 'blockstates', name + '.json'), obj)


def item_model(id, model_ref):
    dump(os.path.join(A, 'items', id + '.json'), {'model': {'type': 'minecraft:model', 'model': model_ref}})


def item_generated(id, texture_ref):
    dump(os.path.join(A, 'models/item', id + '.json'), {'parent': 'minecraft:item/generated', 'textures': {'layer0': texture_ref}})
    item_model(id, f'{NS}:item/{id}')


FACING_Y = dict(north=0, east=90, south=180, west=270)

# ------------------------------------------------------------------ registry bookkeeping
java_lines = []      # field declarations
names = {}           # lang
loot = {}            # id -> loot kind
recipes = []         # (id, ingredient, count)
tags = {k: [] for k in ('pickaxe', 'axe', 'shovel', 'walls', 'stairs', 'slabs', 'doors', 'trapdoors', 'climbable')}
cutout, translucent = [], []

INGREDIENT = dict(metal='minecraft:iron_ingot', heavy='minecraft:iron_ingot', stone='minecraft:stone', wood='minecraft:oak_planks',
                  glass='minecraft:glass', cloth='minecraft:white_wool', plastic=f'{NS}:plastic_block', paint='minecraft:paper',
                  rubble='minecraft:gravel')
TOOL = dict(metal='pickaxe', heavy='pickaxe', stone='pickaxe', plastic='pickaxe', wood='axe', rubble='shovel')


def const(id):
    return id.upper()


def register(id, name, mat, factory, props, door=False, extra_tags=(), loot_kind='self', ingredient=None, count=1):
    fn = 'door' if door else 'block'
    java_lines.append(f'    public static final Block {const(id)} = {fn}("{id}", {factory}, {props});')
    names[id] = name
    loot[id] = loot_kind
    if mat in TOOL:
        tags[TOOL[mat]].append(id)
    for t in extra_tags:
        tags[t].append(id)
    recipes.append((id, ingredient or INGREDIENT[mat], count))


def props(mat, extra=''):
    return f'{mat}(){extra}'


def light_expr(e):
    lvl = e.get('light')
    if not lvl:
        return ''
    if e['kind'] == 'light' and e.get('lightmode') in ('switch', 'alarm'):
        return f'.lightLevel(s -> LightBlock.light(s, {lvl}))'
    return f'.lightLevel(s -> {lvl})'


def jnum(v):
    return ('%g' % v) if v != int(v) else str(int(v))


def spec_expr(e, boxes, collide, mount):
    arr = ', '.join('{' + ', '.join(jnum(v) for v in b[:6]) + '}' for b in boxes)
    particle = f'() -> ParticleTypes.{e["particle"]}' if e.get('particle') else 'null'
    return (f'new ShapedBlock.Spec(new double[][]{{{arr}}}, {str(collide).lower()}, ShapedBlock.Mount.{mount.upper()}, '
            f'{particle}, {str(bool(e.get("sticky"))).lower()})')


# ------------------------------------------------------------------ models for the plain shapes
def cube_models(id, t):
    model(id, {'parent': 'minecraft:block/cube_all', 'textures': {'all': tex_ref(t['all'])}})
    blockstate(id, {'variants': {'': {'model': f'{NS}:block/{id}'}}})
    item_model(id, f'{NS}:block/{id}')


def stair_models(id, tx):
    """tx: bottom, top, side texture refs."""
    for suffix, parent in (('', 'stairs'), ('_inner', 'inner_stairs'), ('_outer', 'outer_stairs')):
        model(id + suffix, {'parent': f'minecraft:block/{parent}', 'textures': tx})
    tpl = json.load(open(os.path.join(A, 'blockstates/blue_tile_stairs.json')))
    blockstate(id, json.loads(json.dumps(tpl).replace(f'{NS}:block/blue_tile_stairs', f'{NS}:block/{id}')))
    item_model(id, f'{NS}:block/{id}')


def slab_models(id, tx, full_model):
    model(id, {'parent': 'minecraft:block/slab', 'textures': tx})
    model(id + '_top', {'parent': 'minecraft:block/slab_top', 'textures': tx})
    blockstate(id, {'variants': {'type=bottom': {'model': f'{NS}:block/{id}'}, 'type=top': {'model': f'{NS}:block/{id}_top'},
                                 'type=double': {'model': full_model}}})
    item_model(id, f'{NS}:block/{id}')


def wall_models(id, t):
    w = {'wall': t}
    model(id + '_post', {'parent': 'minecraft:block/template_wall_post', 'textures': w})
    model(id + '_side', {'parent': 'minecraft:block/template_wall_side', 'textures': w})
    model(id + '_side_tall', {'parent': 'minecraft:block/template_wall_side_tall', 'textures': w})
    model(id + '_inventory', {'parent': 'minecraft:block/wall_inventory', 'textures': w})
    parts = [{'when': {'up': 'true'}, 'apply': {'model': f'{NS}:block/{id}_post'}}]
    for h, m in (('low', '_side'), ('tall', '_side_tall')):
        for d, y in (('north', 0), ('east', 90), ('south', 180), ('west', 270)):
            a = {'model': f'{NS}:block/{id}{m}', 'uvlock': True}
            if y:
                a['y'] = y
            parts.append({'when': {d: h}, 'apply': a})
    blockstate(id, {'multipart': parts})
    item_model(id, f'{NS}:block/{id}_inventory')


def pane_models(id, t):
    tx = {'pane': t, 'edge': t}
    for part in ('post', 'side', 'side_alt', 'noside', 'noside_alt'):
        model(f'{id}_{part}', {'parent': f'minecraft:block/template_glass_pane_{part}', 'textures': tx})
    m = lambda part, y=0: dict({'model': f'{NS}:block/{id}_{part}'}, **({'y': y} if y else {}))  # noqa: E731
    blockstate(id, {'multipart': [
        {'apply': m('post')},
        {'when': {'north': 'true'}, 'apply': m('side')},
        {'when': {'east': 'true'}, 'apply': m('side', 90)},
        {'when': {'south': 'true'}, 'apply': m('side_alt')},
        {'when': {'west': 'true'}, 'apply': m('side_alt', 90)},
        {'when': {'north': 'false'}, 'apply': m('noside')},
        {'when': {'east': 'false'}, 'apply': m('noside_alt')},
        {'when': {'south': 'false'}, 'apply': m('noside_alt', 90)},
        {'when': {'west': 'false'}, 'apply': m('noside', 270)},
    ]})
    item_generated(id, t)


def facing_variants(model_for):
    """Blockstate variants over facing (plus whatever extra keys model_for returns)."""
    out = {}
    for f, y in FACING_Y.items():
        for extra, m in model_for():
            key = f'facing={f}' + (',' + extra if extra else '')
            v = {'model': f'{NS}:block/{m}'}
            if y:
                v['y'] = y
            out[key] = v
    return {'variants': out}


def element_model(boxes, texmap, front, top, ymin=0, ymax=16):
    """Block model from boxes clipped to [ymin, ymax) and shifted down by ymin."""
    keys, elements = {}, []

    def key(ref):
        if ref not in keys:
            keys[ref] = f't{len(keys)}'
        return '#' + keys[ref]
    for b in boxes:
        y1, y2 = max(b[1], ymin) - ymin, min(b[4], ymax) - ymin
        if y2 <= y1:
            continue
        base = key(texmap(b[6]))
        faces = {d: {'texture': base} for d in ('down', 'up', 'north', 'south', 'west', 'east')}
        if len(b) > 7:
            faces['north'] = {'texture': key(texmap(b[7]))}
        if top:
            faces['up'] = {'texture': key(top)}
        elements.append({'from': [b[0], y1, b[2]], 'to': [b[3], y2, b[5]], 'faces': faces})
    textures = {name: ref for ref, name in keys.items()}
    textures['particle'] = next(iter(keys)) if keys else tex_ref('mat_steel')
    return {'parent': 'minecraft:block/block', 'textures': textures, 'elements': elements}


# ------------------------------------------------------------------ main loop
def own_textures(e):
    """Paint the block's own textures; returns {key: texture name}."""
    out = {}
    for k, painter in e['tex'].items():
        name = e['id'] if k in ('all', 'self') else f'{e["id"]}_{k}'
        paint_tex(name, painter)
        out[k] = name
    return out


GLASS_MATS = {'glass', 'blue_glass', 'liquid'}


def lamp_names(e, on):
    k = e.get('lamp', 'white')
    if k == 'screen':
        return mat_tex('screen_on' if on else 'screen_off')
    if on:
        return mat_tex('lamp_' + k)
    off = f'lamp_{k}_off'
    return mat_tex(off if off in MATS else 'lamp_off')


def do_shaped(e, own):
    id, kind = e['id'], e['kind']
    boxes = e.get('boxes')
    mount = e.get('mount', 'floor')
    collide = e.get('collide', True)
    if kind == 'decal_wall':
        boxes, mount, collide = [(0, 0, 15.9, 16, 16, 16, 'self')], 'wall', False
    elif kind == 'decal_floor':
        boxes, collide = [(0, 0, 0, 16, 0.1, 16, 'self')], False
    tall = kind == 'tall'
    switch = kind == 'light' and e.get('lightmode') in ('switch', 'alarm')

    def texmap_for(on):
        def texmap(name):
            if name == 'LAMP':
                return tex_ref(lamp_names(e, on))
            if name == 'self':
                return tex_ref(own['self'])
            if name == 'front':
                return tex_ref(own['front'])
            if name in own:
                return tex_ref(own[name])
            return tex_ref(mat_tex(name))
        return texmap
    top = tex_ref(own['top']) if e.get('toptex') else None

    if e.get('model') == 'cross':
        model(id, {'parent': 'minecraft:block/cross', 'textures': {'cross': tex_ref(own['self'])}})
        blockstate(id, facing_variants(lambda: [('', id)]))
        item_generated(id, tex_ref(own['self']))
    elif tall:
        model(id + '_lower', element_model(boxes, texmap_for(True), None, top, 0, 16))
        model(id + '_upper', element_model(boxes, texmap_for(True), None, top, 16, 32))
        blockstate(id, facing_variants(lambda: [('half=lower', id + '_lower'), ('half=upper', id + '_upper')]))
        item_model(id, f'{NS}:block/{id}_lower')
    elif switch:
        model(id, element_model(boxes, texmap_for(True), None, top))
        model(id + '_off', element_model(boxes, texmap_for(False), None, top))
        blockstate(id, facing_variants(lambda: [('lit=true', id), ('lit=false', id + '_off')]))
        item_model(id, f'{NS}:block/{id}')
    else:
        model(id, element_model(boxes, texmap_for(True), None, top))
        blockstate(id, facing_variants(lambda: [('', id)]))
        if kind.startswith('decal'):
            item_generated(id, tex_ref(own['self']))
        else:
            item_model(id, f'{NS}:block/{id}')

    used = {b[6] for b in boxes} | {b[7] for b in boxes if len(b) > 7}
    if e.get('translucent') or used & GLASS_MATS:
        translucent.append(id)
    elif e.get('cutout') or e.get('model') == 'cross' or kind.startswith('decal'):
        cutout.append(id)

    spec = spec_expr(e, boxes, collide, mount)
    extra = '.noOcclusion()' + light_expr(e)
    if not collide:
        extra += '.noCollision()'
    if tall:
        factory = f'p -> new TallShapedBlock(p, {spec})'
    elif switch:
        factory = f'p -> new LightBlock(p, {spec}, {str(e["lightmode"] == "alarm").lower()})'
    else:
        factory = f'p -> new ShapedBlock(p, {spec})'
    ing = 'minecraft:glowstone_dust' if kind == 'light' else None
    register(id, e['name'], e['mat'], factory, props(e['mat'], extra), ingredient=ing, count=4 if e['mat'] == 'paint' else 1)


def do_entry(e):
    id, kind, mat = e['id'], e['kind'], e['mat']
    own = own_textures(e)
    variants = e.get('variants', [])
    full_tx = None   # textures for stairs/slabs/walls
    full_model = f'{NS}:block/{id}'

    if kind in ('cube', 'glass'):
        cube_models(id, own)
        t = tex_ref(own['all'])
        full_tx = {'bottom': t, 'top': t, 'side': t}
        if kind == 'glass':
            (translucent if e.get('translucent') else cutout).append(id)
            factory = 'TransparentBlock::new'
            extra = '.noOcclusion().isValidSpawn((s, l, p, e) -> false).isRedstoneConductor((s, l, p) -> false).isSuffocating((s, l, p) -> false).isViewBlocking((s, l, p, a) -> false)'
        else:
            factory, extra = 'Block::new', ''
        register(id, e['name'], mat, factory, props(mat, extra + light_expr(e)))
    elif kind == 'cube_top':
        tx = {k: tex_ref(own[k]) for k in ('top', 'side', 'bottom')}
        model(id, {'parent': 'minecraft:block/cube_bottom_top', 'textures': tx})
        blockstate(id, {'variants': {'': {'model': f'{NS}:block/{id}'}}})
        item_model(id, f'{NS}:block/{id}')
        register(id, e['name'], mat, 'Block::new', props(mat))
    elif kind == 'pillar':
        tx = {'end': tex_ref(own['end']), 'side': tex_ref(own['side'])}
        model(id, {'parent': 'minecraft:block/cube_column', 'textures': tx})
        model(id + '_horizontal', {'parent': 'minecraft:block/cube_column_horizontal', 'textures': tx})
        blockstate(id, {'variants': {'axis=y': {'model': f'{NS}:block/{id}'},
                                     'axis=z': {'model': f'{NS}:block/{id}_horizontal', 'x': 90},
                                     'axis=x': {'model': f'{NS}:block/{id}_horizontal', 'x': 90, 'y': 90}}})
        item_model(id, f'{NS}:block/{id}')
        register(id, e['name'], mat, 'RotatedPillarBlock::new', props(mat))
    elif kind == 'facing':
        model(id, {'parent': 'minecraft:block/orientable', 'textures': {k: tex_ref(own[k]) for k in ('front', 'side', 'top')}})
        blockstate(id, facing_variants(lambda: [('', id)]))
        item_model(id, f'{NS}:block/{id}')
        register(id, e['name'], mat, 'FacingBlock::new', props(mat, light_expr(e)))
    elif kind == 'pane':
        pane_models(id, tex_ref(own['all']))
        cutout.append(id)
        register(id, e['name'], mat, 'DecoPaneBlock::new', props(mat, '.noOcclusion()'))
    elif kind == 'carpet':
        model(id, {'parent': 'minecraft:block/carpet', 'textures': {'wool': tex_ref(own['all'])}})
        blockstate(id, {'variants': {'': {'model': f'{NS}:block/{id}'}}})
        item_model(id, f'{NS}:block/{id}')
        register(id, e['name'], mat, 'CarpetBlock::new', props(mat), count=3)
    elif kind == 'door':
        tx = {'bottom': tex_ref(own['bottom']), 'top': tex_ref(own['top'])}
        variants_out = {}
        for half in ('lower', 'upper'):
            for hinge in ('left', 'right'):
                for opened in (False, True):
                    m = f'{id}_{"bottom" if half == "lower" else "top"}_{hinge}' + ('_open' if opened else '')
                    model(m, {'parent': f'minecraft:block/door_{"bottom" if half == "lower" else "top"}_{hinge}' + ('_open' if opened else ''), 'textures': tx})
                    for f, base in dict(east=0, south=90, west=180, north=270).items():
                        y = (base + (0 if not opened else 90 if hinge == 'left' else 270)) % 360
                        v = {'model': f'{NS}:block/{m}'}
                        if y:
                            v['y'] = y
                        variants_out[f'facing={f},half={half},hinge={hinge},open={str(opened).lower()}'] = v
        blockstate(id, {'variants': variants_out})
        door_item_texture(id, own)
        cutout.append(id)
        factory = f'p -> new DoorBlock(BlockSetType.{e.get("set", "IRON")}, p)'
        register(id, e['name'], mat, factory, props(mat, '.noOcclusion()'), door=True, extra_tags=['doors'], loot_kind='door')
    elif kind == 'sliding_door':
        for part in ('bottom', 'top', 'bottom_open_left', 'bottom_open_right', 'top_open_left', 'top_open_right'):
            src = json.load(open(os.path.join(A, f'models/block/sliding_door_{part}.json')))
            s = json.dumps(src)
            s = s.replace(f'{NS}:block/sliding_door_bottom', tex_ref(own['bottom'])).replace(f'{NS}:block/sliding_door_top', tex_ref(own['top']))
            model(f'{id}_{part}', json.loads(s))
        src = json.dumps(json.load(open(os.path.join(A, 'blockstates/sliding_door.json'))))
        blockstate(id, json.loads(src.replace(f'{NS}:block/sliding_door_', f'{NS}:block/{id}_')))
        door_item_texture(id, own)
        register(id, e['name'], mat, 'SlidingDoorBlock::new', props(mat, '.noOcclusion()'), door=True, extra_tags=['doors'], loot_kind='door')
    elif kind == 'trapdoor':
        t = tex_ref(own['all'])
        for part in ('bottom', 'top', 'open'):
            model(f'{id}_{part}', {'parent': f'minecraft:block/template_orientable_trapdoor_{part}', 'textures': {'texture': t}})
        out = {}
        for f, y in FACING_Y.items():
            for half in ('bottom', 'top'):
                for opened in (False, True):
                    if opened:
                        v = {'model': f'{NS}:block/{id}_open'}
                        if half == 'top':
                            v['x'] = 180
                            yy = (y + 180) % 360
                        else:
                            yy = y
                    else:
                        v = {'model': f'{NS}:block/{id}_{half}'}
                        yy = y
                    if yy:
                        v['y'] = yy
                    out[f'facing={f},half={half},open={str(opened).lower()}'] = v
        blockstate(id, {'variants': out})
        item_model(id, f'{NS}:block/{id}_bottom')
        if e.get('cutout', True):
            cutout.append(id)
        set_type = {'IRON_HAND': 'COPPER'}.get(e.get('set', 'IRON'), e.get('set', 'IRON'))
        if id == 'metal_grate_trapdoor':
            set_type = 'COPPER'
        register(id, e['name'], mat, f'p -> new DecoTrapDoorBlock(BlockSetType.{set_type}, p)', props(mat, '.noOcclusion()'), extra_tags=['trapdoors'])
    elif kind == 'ladder':
        t = tex_ref(own['all'])
        model(id, {'ambientocclusion': False, 'textures': {'particle': t, 'texture': t}, 'elements': [
            {'from': [0, 0, 15.2], 'to': [16, 16, 15.2], 'shade': False,
             'faces': {'north': {'uv': [0, 0, 16, 16], 'texture': '#texture'}, 'south': {'uv': [16, 0, 0, 16], 'texture': '#texture'}}}]})
        blockstate(id, facing_variants(lambda: [('', id)]))
        item_generated(id, t)
        cutout.append(id)
        register(id, e['name'], mat, 'DecoLadderBlock::new', props(mat, '.noOcclusion()'), extra_tags=['climbable'], count=3)
    elif kind == 'layer':
        t = tex_ref(own['all'])
        out = {}
        for n in range(1, 9):
            h = n * 2
            if h == 16:
                model(f'{id}_height16', {'parent': 'minecraft:block/cube_all', 'textures': {'all': t}})
            else:
                model(f'{id}_height{h}', {'parent': 'minecraft:block/block', 'textures': {'particle': t, 'texture': t}, 'elements': [
                    {'from': [0, 0, 0], 'to': [16, h, 16], 'faces': {
                        'down': {'texture': '#texture', 'cullface': 'down'}, 'up': {'texture': '#texture'},
                        'north': {'texture': '#texture', 'cullface': 'north'}, 'south': {'texture': '#texture', 'cullface': 'south'},
                        'west': {'texture': '#texture', 'cullface': 'west'}, 'east': {'texture': '#texture', 'cullface': 'east'}}}]})
            out[f'layers={n}'] = {'model': f'{NS}:block/{id}_height{h}'}
        blockstate(id, {'variants': out})
        item_model(id, f'{NS}:block/{id}_height2')
        register(id, e['name'], mat, 'LayerBlock::new', props(mat, '.noOcclusion()'), count=4)
    elif kind == 'axis':
        hx, hz = e['half']
        t = tex_ref(own['self'])
        model(id, {'parent': 'minecraft:block/block', 'textures': {'particle': t, 'side': t}, 'elements': [
            {'from': [8 - hx, 0, 8 - hz], 'to': [8 + hx, 16, 8 + hz], 'faces': {d: {'texture': '#side'} for d in ('down', 'up', 'north', 'south', 'west', 'east')}}]})
        blockstate(id, {'variants': {'axis=y': {'model': f'{NS}:block/{id}'}, 'axis=z': {'model': f'{NS}:block/{id}', 'x': 90},
                                     'axis=x': {'model': f'{NS}:block/{id}', 'x': 90, 'y': 90}}})
        item_model(id, f'{NS}:block/{id}')
        particle = f'() -> ParticleTypes.{e["particle"]}' if e.get('particle') else 'null'
        register(id, e['name'], mat, f'p -> new AxisShapedBlock(p, {hx}, {hz}, {particle})', props(mat, '.noOcclusion()'), count=4)
    else:
        do_shaped(e, own)

    # stairs / slabs / walls / panes made from this block
    for v in variants:
        vid = f'{id}_{v}'
        if v == 'pane':
            pane_models(vid, tex_ref(own['all']))
            (translucent if e.get('translucent') else cutout).append(vid)
            register(vid, e['name'] + ' Pane', mat, 'DecoPaneBlock::new', props(mat, '.noOcclusion()'), ingredient=f'{NS}:{id}', count=2)
            continue
        see_through = bool(e.get('cutout') or kind == 'glass')
        vextra = '.noOcclusion()' if see_through else ''
        if see_through:
            cutout.append(vid)
        if v == 'stairs':
            stair_models(vid, full_tx)
            register(vid, e['name'] + ' Stairs', mat, f'p -> new StairBlock({const(id)}.defaultBlockState(), p)', props(mat, vextra),
                     extra_tags=['stairs'], ingredient=f'{NS}:{id}')
        elif v == 'slab':
            slab_models(vid, full_tx, full_model)
            register(vid, e['name'] + ' Slab', mat, 'SlabBlock::new', props(mat, vextra), extra_tags=['slabs'], loot_kind='slab',
                     ingredient=f'{NS}:{id}', count=2)
        elif v == 'wall':
            wall_models(vid, full_tx['side'])
            register(vid, e['name'] + ' Wall', mat, 'WallBlock::new', props(mat), extra_tags=['walls'], ingredient=f'{NS}:{id}')


def door_item_texture(id, own):
    os.makedirs(ITEM_TEX_DIR, exist_ok=True)
    path = os.path.join(ITEM_TEX_DIR, id + '.png')
    sources = [os.path.join(TEX_DIR, own[k] + '.png') for k in ('top', 'bottom')]
    if not os.path.exists(path) and all(os.path.exists(f) for f in sources):
        top = Image.open(os.path.join(TEX_DIR, own['top'] + '.png')).resize((8, 8), Image.NEAREST)
        bottom = Image.open(os.path.join(TEX_DIR, own['bottom'] + '.png')).resize((8, 8), Image.NEAREST)
        im = Image.new('RGBA', (16, 16), (0, 0, 0, 0))
        im.paste(top, (4, 0))
        im.paste(bottom, (4, 8))
        im.save(path)
    item_generated(id, f'{NS}:item/deco/{id}')


# ------------------------------------------------------------------ data files
def write_loot(id, kind):
    entry = {'type': 'minecraft:item', 'name': f'{NS}:{id}'}
    conditions = [{'condition': 'minecraft:survives_explosion'}]
    if kind == 'slab':
        entry['functions'] = [{'function': 'minecraft:set_count', 'count': 2, 'add': False, 'conditions': [
            {'condition': 'minecraft:block_state_property', 'block': f'{NS}:{id}', 'properties': {'type': 'double'}}]},
            {'function': 'minecraft:explosion_decay'}]
        conditions = []
    elif kind == 'door':
        conditions.append({'condition': 'minecraft:block_state_property', 'block': f'{NS}:{id}', 'properties': {'half': 'lower'}})
    pool = {'rolls': 1, 'bonus_rolls': 0, 'entries': [entry]}
    if conditions:
        pool['conditions'] = conditions
    dump(os.path.join(D, 'loot_table/blocks', id + '.json'), {'type': 'minecraft:block', 'pools': [pool], 'random_sequence': f'{NS}:blocks/{id}'})


def merge_tag(path, values):
    vals = []
    if os.path.exists(path):
        vals = json.load(open(path)).get('values', [])
    for v in values:
        if v not in vals:
            vals.append(v)
    dump(path, {'values': vals})


def write_java():
    src = f'''package dev.projectredacted.registry;

import dev.projectredacted.ProjectRedacted;
import dev.projectredacted.block.FacingBlock;
import dev.projectredacted.block.SlidingDoorBlock;
import dev.projectredacted.block.deco.*;
import dev.projectredacted.block.deco.LightBlock;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.DoubleHighBlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.*;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.BlockSetType;
import net.minecraft.world.level.material.MapColor;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * The decorative blocks from design/deco-blocks-list.md. GENERATED by tools/deco/gen.py from tools/deco/specs.py;
 * edit the spec and re-run rather than editing this file.
 */
public final class DecoBlocks {{
    public static final List<Block> ALL = new ArrayList<>();

{chr(10).join(java_lines)}

    private static BlockBehaviour.Properties metal() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(3f, 6f).sound(SoundType.METAL).requiresCorrectToolForDrops();
    }}

    private static BlockBehaviour.Properties heavy() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_GRAY).strength(8f, 600f).sound(SoundType.NETHERITE_BLOCK).requiresCorrectToolForDrops();
    }}

    private static BlockBehaviour.Properties stone() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(2.5f, 8f).requiresCorrectToolForDrops();
    }}

    private static BlockBehaviour.Properties wood() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOD).strength(2f, 3f).sound(SoundType.WOOD).ignitedByLava();
    }}

    private static BlockBehaviour.Properties glass() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.6f).sound(SoundType.GLASS);
    }}

    private static BlockBehaviour.Properties cloth() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.WOOL).strength(0.8f).sound(SoundType.WOOL).ignitedByLava();
    }}

    private static BlockBehaviour.Properties plastic() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.SNOW).strength(1.5f, 4f).sound(SoundType.BAMBOO_WOOD);
    }}

    private static BlockBehaviour.Properties paint() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.NONE).strength(0.2f).sound(SoundType.WOOL);
    }}

    private static BlockBehaviour.Properties rubble() {{
        return BlockBehaviour.Properties.of().mapColor(MapColor.STONE).strength(0.6f).sound(SoundType.GRAVEL);
    }}

    private static Block block(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {{
        Identifier id = ProjectRedacted.id(name);
        Block block = factory.apply(props.setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Item.Properties itemProps = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        Registry.register(BuiltInRegistries.ITEM, id, new BlockItem(block, itemProps));
        ALL.add(block);
        return block;
    }}

    private static Block door(String name, Function<BlockBehaviour.Properties, Block> factory, BlockBehaviour.Properties props) {{
        Identifier id = ProjectRedacted.id(name);
        Block block = factory.apply(props.setId(ResourceKey.create(Registries.BLOCK, id)));
        Registry.register(BuiltInRegistries.BLOCK, id, block);
        Item.Properties itemProps = new Item.Properties().setId(ResourceKey.create(Registries.ITEM, id)).useBlockDescriptionPrefix();
        Registry.register(BuiltInRegistries.ITEM, id, new DoubleHighBlockItem(block, itemProps));
        ALL.add(block);
        return block;
    }}

    public static void init() {{}}

    private DecoBlocks() {{}}
}}
'''
    with open(JAVA, 'w') as f:
        f.write(src)


def main():
    seen = set()
    for e in B:
        assert e['id'] not in seen, e['id']
        seen.add(e['id'])
        do_entry(e)
    for id, kind in loot.items():
        write_loot(id, kind)
    for id, ing, count in recipes:
        dump(os.path.join(D, 'recipe', f'{id}_from_stonecutting.json'),
             {'type': 'minecraft:stonecutting', 'ingredient': ing, 'result': {'id': f'{NS}:{id}', 'count': count}})
    for tool in ('pickaxe', 'axe', 'shovel'):
        merge_tag(os.path.join(MC, f'block/mineable/{tool}.json'), [f'{NS}:{i}' for i in tags[tool]])
    for t in ('walls', 'stairs', 'slabs', 'doors', 'trapdoors'):
        merge_tag(os.path.join(MC, f'block/{t}.json'), [f'{NS}:{i}' for i in tags[t]])
        merge_tag(os.path.join(MC, f'item/{t}.json'), [f'{NS}:{i}' for i in tags[t]])
    merge_tag(os.path.join(MC, 'block/climbable.json'), [f'{NS}:{i}' for i in tags['climbable']])
    lang_path = os.path.join(A, 'lang/en_us.json')
    lang = json.load(open(lang_path))
    lang['itemGroup.projectredacted.deco'] = 'Project [REDACTED]: Decoration'
    for id, name in names.items():
        lang[f'block.{NS}.{id}'] = name
    dump(lang_path, lang)
    write_java()
    print(f'{len(names)} blocks, {len(written_tex)} textures, {len(cutout)} cutout, {len(translucent)} translucent')


if __name__ == '__main__':
    main()
