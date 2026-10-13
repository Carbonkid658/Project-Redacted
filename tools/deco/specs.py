"""The deco block list (design/deco-blocks-list.md), one entry per block.

kind:    cube, cube_top, pillar, facing, glass, pane, carpet, door, sliding_door, trapdoor, ladder, layer, axis,
         shaped, tall, light, decal_wall, decal_floor
mat:     metal, heavy, stone, wood, glass, cloth, plastic, paint, rubble (strength, sound, tool, recipe input)
boxes:   (x1, y1, z1, x2, y2, z2, tex[, front tex]) in pixels, front of the block at z = 0
tex:     names in MATS, 'self' (the block's own texture), 'front' (the block's own _front texture) or 'LAMP'
"""
import paint as P
from tex import shade

B = []


def add(id, name, kind, mat, tex=None, **kw):
    e = dict(id=id, name=name, kind=kind, mat=mat, tex=tex or {}, **kw)
    B.append(e)
    return e


# ---------- shared materials for shaped blocks ----------
MATS = dict(
    steel=P.panel('steel'), dsteel=P.panel('dsteel'), black=P.panel('black', bev=False), dgrey=P.panel('dgrey'),
    white=P.panel('plastic', 0.02), offwhite=P.panel('offwhite'), wood=P.wood_grain('wood'), dwood=P.wood_grain('dwood'),
    red=P.panel('red'), yellow=P.panel('yellow'), blue=P.panel('blue'), green=P.panel('green'), orange=P.panel('orange'),
    concrete=P.concrete(), rubber=P.ribs((40, 40, 42)), cushion=P.cloth((60, 60, 70), False),
    mattress=P.cloth((215, 215, 205)), cloth_blue=P.cloth((60, 90, 150)), cloth_brown=P.cloth((120, 84, 60)),
    glass=P.glass(), screen_on=P.screen(True), screen_off=P.screen(False), copper=P.panel('copper'),
    lamp_white=P.lamp((255, 250, 225)), lamp_red=P.lamp((255, 60, 40)), lamp_amber=P.lamp((255, 170, 40)),
    lamp_purple=P.lamp((190, 90, 255)), lamp_green=P.lamp((90, 255, 120)), lamp_blue=P.lamp((90, 160, 255)),
    lamp_off=P.lamp((90, 90, 86)), lamp_red_off=P.lamp((90, 30, 26)), lamp_amber_off=P.lamp((100, 70, 30)),
    rust=P.steel('steel', 0.8, False, False), hazard=P.stripes('yellow', 'black', 3), redstripe=P.stripes('red', 'white', 3),
    coil=P.ribs((184, 112, 70), 2), glow_green=P.lamp((120, 255, 140)), acid=P.organic((110, 220, 60)),
    liquid=P.glass((120, 220, 200), 160, None, tinted=True), paper=P.solid((236, 232, 222), 0.02), tray=P.panel((150, 160, 170)),
    leaves=P.decal((60, 130, 50), 'leaves'), soil=P.solid((80, 60, 40), 0.1), terracotta=P.panel((170, 90, 60)),
    blue_glass=P.glass((90, 150, 220), 140, None, tinted=True), lgrey=P.panel('lgrey'), lead=P.panel('lead'),
    foam=P.foam((70, 70, 76)), khaki=P.cloth('khaki'), olive=P.panel('olive'),
)

# ---------- shape helpers ----------
FULL = (0, 0, 0, 16, 16, 16)


def plate(x1, y1, x2, y2, depth=1, tex='self'):
    """A plate on the wall behind the block."""
    return (x1, y1, 16 - depth, x2, y2, 16, tex)


def paint_wall():
    return [(0, 0, 15.9, 16, 16, 16, 'self')]


def paint_floor():
    return [(0, 0, 0, 16, 0.1, 16, 'self')]


def ceiling(x1, z1, x2, z2, h=2, tex='LAMP'):
    return (x1, 16 - h, z1, x2, 16, z2, tex)


def legs(y, inset=1, w=2, tex='steel'):
    a, b = inset, 16 - inset - w
    return [(a, 0, a, a + w, y, a + w, tex), (b, 0, a, b + w, y, a + w, tex), (a, 0, b, a + w, y, b + w, tex), (b, 0, b, b + w, y, b + w, tex)]


SB = ['stairs', 'slab']
SBW = ['stairs', 'slab', 'wall']
TILE_COLOURS = dict(blue='ftile', security='security', office='office', sage='sage')
WHITE_WALL = (222, 214, 214)

# ======================= 1. Walls and structural =======================
add('facility_wall_trim', 'Facility Wall Trim', 'cube', 'stone', {'all': P.trim(WHITE_WALL, (150, 30, 30))}, variants=SB)
for k, c in TILE_COLOURS.items():
    add(f'facility_wall_trim_{k}', f'Facility Wall Trim ({k.title()})', 'cube', 'stone', {'all': P.trim(WHITE_WALL, c)}, variants=SB)
add('facility_wall_upper_trim', 'Facility Wall Upper Trim', 'cube', 'stone', {'all': P.trim(WHITE_WALL, 'security', 1, 4)}, variants=SB)
add('hazard_wall', 'Hazard-Stripe Wall', 'cube', 'stone', {'all': P.stripes('yellow', 'black', 4)}, variants=SB)
add('red_hazard_wall', 'Red Hazard-Stripe Wall', 'cube', 'stone', {'all': P.stripes('red', 'white', 4)}, variants=SB)
add('reinforced_concrete', 'Reinforced Concrete', 'cube', 'stone', {'all': P.concrete()}, variants=SBW)
add('cracked_reinforced_concrete', 'Cracked Reinforced Concrete', 'cube', 'stone', {'all': P.concrete(cracks=3, ties=True)}, variants=SB)
add('concrete_pillar', 'Concrete Pillar', 'pillar', 'stone',
    {'side': P.with_overlay(P.concrete(ties=False), lambda t: (t.vline(0, shade('concrete', .7)), t.vline(15, shade('concrete', .7)))),
     'end': P.panel('concrete', 0.06)})
add('steel_beam', 'Steel Support Beam', 'pillar', 'metal',
    {'side': P.with_overlay(P.panel('dsteel'), lambda t: (t.fill(shade('dsteel', .8), 0, 0, 3, 16), t.fill(shade('dsteel', .8), 13, 0, 16, 16), t.rivets('lgrey', 1, 4))),
     'end': P.with_overlay(P.solid((40, 40, 44)), lambda t: (t.fill('dsteel', 1, 1, 15, 4), t.fill('dsteel', 1, 12, 15, 15), t.fill('dsteel', 6, 4, 10, 12)))})
add('riveted_steel_plate', 'Riveted Steel Plate', 'cube', 'heavy', {'all': P.steel()}, variants=SB)
add('rusted_steel_plate', 'Rusted Steel Plate', 'cube', 'heavy', {'all': P.steel('steel', 0.9)}, variants=SB)
add('lead_panel', 'Lead-Lined Panel', 'cube', 'heavy', {'all': P.with_overlay(P.panel('lead'), lambda t: P.ICONS['trefoil'](t, (220, 200, 60), big=False))}, variants=SB)
add('acoustic_panel', 'Acoustic Foam Panel', 'cube', 'cloth', {'all': P.foam((70, 70, 76))})
add('padded_wall', 'Padded Wall', 'cube', 'cloth', {'all': P.padded((236, 234, 228))}, variants=SB)
add('faraday_mesh', 'Faraday Mesh', 'pane', 'metal', {'all': P.mesh((190, 110, 70), 3)})
add('ceramic_containment_tile', 'Ceramic Containment Tile', 'cube', 'stone', {'all': P.tiles((244, 244, 240), (200, 200, 196), 4, 0.02)}, variants=SB)
add('corrugated_metal', 'Corrugated Metal', 'cube', 'metal', {'all': P.corrugated('steel')}, variants=SB)
add('rusted_corrugated_metal', 'Rusted Corrugated Metal', 'cube', 'metal', {'all': P.corrugated('steel', 0.8)}, variants=SB)
add('drywall', 'Drywall', 'cube', 'plastic', {'all': P.solid((228, 226, 218), 0.02)}, variants=SB)
add('damaged_drywall', 'Damaged Drywall', 'cube', 'plastic', {'all': P.with_overlay(P.solid((228, 226, 218), 0.02), lambda t: (P.cracks(t, 2), t.fill((150, 130, 100), 5, 6, 10, 11)))}, variants=SB)
add('wood_panelling', 'Wood Panelling', 'cube', 'wood', {'all': P.planks((96, 62, 40))}, variants=SB)
add('cinder_block', 'Cinder Block', 'cube', 'stone', {'all': P.brick((150, 150, 146), (110, 110, 106), 4, 8)}, variants=SBW)
for k, c in dict(white=(222, 222, 216), blue=(110, 140, 180), green=(130, 160, 130)).items():
    add(f'{k}_cinder_block', f'{k.title()} Painted Cinder Block', 'cube', 'stone', {'all': P.brick(c, shade(c, .8), 4, 8)}, variants=SB)
add('old_site_brick', 'Old Site Brick', 'cube', 'stone', {'all': P.brick('brick')}, variants=SBW)
add('cracked_old_site_brick', 'Cracked Old Site Brick', 'cube', 'stone', {'all': P.brick('brick', cracks=2)}, variants=SB)
add('bulkhead_frame', 'Bulkhead Frame', 'shaped', 'heavy', {'self': P.steel('dsteel')},
    boxes=[(0, 0, 0, 3, 16, 16, 'self'), (13, 0, 0, 16, 16, 16, 'self'), (3, 13, 0, 13, 16, 16, 'self')])
add('wall_corner_guard', 'Wall Corner Guard', 'shaped', 'metal', {'self': P.diamond_plate('steel')},
    boxes=[(0, 0, 14, 2, 16, 16, 'self'), (0, 0, 13, 4, 16, 14, 'self')], mount='wall', collide=False)
add('insulated_panel', 'Insulated Panel', 'cube', 'metal', {'all': P.ribs((236, 236, 232), 4)}, variants=SB)
add('blast_wall', 'Blast Wall', 'cube', 'heavy', {'all': P.with_overlay(P.steel('dgrey'), lambda t: t.rect('black', 2, 2, 13, 13))})
add('damaged_blast_wall', 'Damaged Blast Wall', 'cube', 'heavy', {'all': P.with_overlay(P.steel('dgrey', 0.4), lambda t: (t.rect('black', 2, 2, 13, 13), P.cracks(t, 3)))})

# ======================= 2. Floors =======================
add('diamond_plate', 'Diamond Plate', 'cube', 'metal', {'all': P.diamond_plate('steel')}, variants=SB)
add('rusted_diamond_plate', 'Rusted Diamond Plate', 'cube', 'metal', {'all': P.diamond_plate('steel', 0.8)}, variants=SB)
add('metal_grate', 'Metal Grate', 'glass', 'metal', {'all': P.grate('dsteel')}, variants=['slab'], cutout=True)
add('metal_grate_trapdoor', 'Metal Grate Trapdoor', 'trapdoor', 'metal', {'all': P.grate('dsteel')}, cutout=True, set='IRON')
add('rubber_mat', 'Rubber Mat', 'carpet', 'cloth', {'all': P.ribs((40, 40, 42))})
add('anti_static_floor', 'Anti-Static Floor', 'cube', 'stone', {'all': P.speckle((150, 152, 156), [(90, 92, 96), (200, 200, 200)])}, variants=SB)
add('raised_floor_panel', 'Raised Floor Panel', 'cube', 'metal', {'all': P.with_overlay(P.panel((180, 182, 186)), lambda t: t.rivets('dgrey', 2))}, variants=['slab'])
add('raised_floor_vent', 'Raised Floor Vent', 'cube', 'metal', {'all': P.with_overlay(P.panel((180, 182, 186)), lambda t: [t.set(x, y, (60, 60, 60)) for y in range(3, 13, 2) for x in range(3, 13, 2)])})
for k, c in dict(grey=(130, 134, 138), green=(70, 130, 90), red=(150, 50, 46)).items():
    add('epoxy_floor' if k == 'grey' else f'{k}_epoxy_floor', 'Epoxy Floor' if k == 'grey' else f'{k.title()} Epoxy Floor', 'cube', 'stone', {'all': P.epoxy(c)}, variants=SB)
add('hazard_floor_edge', 'Hazard Floor Edge', 'decal_floor', 'paint', {'self': P.floor_paint('yellow', 'edge')})
for k in ('yellow', 'white'):
    add(f'{k}_floor_arrow', f'{k.title()} Floor Arrow', 'decal_floor', 'paint', {'self': P.floor_paint(k if k == 'yellow' else (235, 235, 230), 'arrow')})
for k, c in dict(yellow='yellow', white=(235, 235, 230), red='red').items():
    add(f'{k}_zone_line', f'{k.title()} Zone Line', 'decal_floor', 'paint', {'self': P.floor_paint(c, 'line')})
add('floor_drain', 'Floor Drain', 'cube_top', 'stone', {'top': P.drain(P.concrete(ties=False)), 'side': P.concrete(ties=False), 'bottom': P.concrete(ties=False)})
for k, c in dict(blue=(60, 80, 130), grey=(110, 112, 116), red=(130, 50, 50)).items():
    add(f'{k}_office_carpet', f'{k.title()} Office Carpet', 'carpet', 'cloth', {'all': P.speckle(c, [shade(c, .8), shade(c, 1.15)], 40)})
add('linoleum', 'Linoleum', 'cube', 'plastic', {'all': P.with_overlay(P.solid((220, 216, 200), .02), lambda t: [t.fill((90, 100, 90), x, y, x + 4, y + 4) for x in range(0, 16, 8) for y in range(0, 16, 8)] + [t.fill((90, 100, 90), x + 4, y + 4, x + 8, y + 8) for x in range(0, 16, 8) for y in range(0, 16, 8)])}, variants=SB)
add('containment_floor_seal', 'Containment Floor Seal', 'cube_top', 'heavy', {'top': P.seal(P.panel('dsteel')), 'side': P.steel('dsteel'), 'bottom': P.steel('dsteel')})
add('scorched_floor', 'Scorched Floor', 'cube', 'stone', {'all': P.scorched(P.tiles((210, 198, 199), (180, 170, 170), 2))}, variants=['slab'])

# ======================= 3. Ceilings and lighting =======================
add('ceiling_tile', 'Ceiling Tile', 'cube', 'plastic', {'all': P.speckle((232, 230, 224), [(200, 198, 190)], 25)}, variants=['slab'])
add('stained_ceiling_tile', 'Stained Ceiling Tile', 'cube', 'plastic', {'all': P.stained(P.speckle((232, 230, 224), [(200, 198, 190)], 25))}, variants=['slab'])
add('ceiling_vent', 'Ceiling Vent', 'cube', 'metal', {'all': P.vent()})
SW = dict(lightmode='switch')
add('fluorescent_light', 'Fluorescent Light', 'light', 'glass', boxes=[ceiling(1, 5, 15, 11, 2, 'white'), (2, 13.5, 6, 14, 14, 10, 'LAMP')], light=15, lamp='white', **SW)
add('panel_light', 'Panel Light', 'light', 'glass', boxes=[ceiling(1, 1, 15, 15, 1, 'LAMP')], light=15, lamp='white', **SW)
add('caged_light', 'Caged Wall Light', 'light', 'metal', boxes=[plate(5, 4, 11, 12, 2, 'dsteel'), (6, 5, 10, 10, 11, 14, 'LAMP')], mount='wall', light=13, lamp='amber', **SW)
add('caged_ceiling_light', 'Caged Ceiling Light', 'light', 'metal', boxes=[ceiling(5, 5, 11, 11, 2, 'dsteel'), (6, 9, 6, 10, 14, 10, 'LAMP')], light=13, lamp='amber', **SW)
add('emergency_light', 'Emergency Light', 'light', 'metal', boxes=[plate(5, 3, 11, 9, 2, 'dgrey'), (5.5, 3.5, 10, 10.5, 8.5, 14, 'LAMP')], mount='wall', light=12, lamp='red', lightmode='alarm')
add('amber_beacon', 'Amber Warning Light', 'light', 'metal', boxes=[plate(5, 3, 11, 9, 2, 'dgrey'), (5.5, 3.5, 10, 10.5, 8.5, 14, 'LAMP')], mount='wall', light=12, lamp='amber', lightmode='alarm')
add('wall_sconce', 'Wall Sconce', 'light', 'metal', boxes=[plate(5, 5, 11, 11, 1, 'dsteel'), (5, 7, 11, 11, 10, 15, 'LAMP')], mount='wall', light=12, lamp='white', **SW)
add('floodlight', 'Floodlight', 'light', 'metal', boxes=[(5, 0, 5, 11, 2, 11, 'dgrey'), (7, 2, 7, 9, 8, 9, 'dgrey'), (3, 8, 6, 13, 14, 11, 'dgrey', 'LAMP')], light=15, lamp='white', **SW)
for k in ('green', 'red'):
    add(f'{k}_floor_guide_light', f'{k.title()} Floor Guide Light', 'light', 'glass', boxes=[(0, 0, 6, 16, 1, 10, 'LAMP')], light=6, lamp=k, collide=False, lightmode='always')
for k, arrow in (('', None), ('left', 'left'), ('right', 'right')):
    sid = 'exit_sign' + (f'_{k}' if k else '')

    def exit_paint(arrow=arrow):
        def f(t):
            t.fill((30, 140, 60))
            t.rect((240, 240, 240), 0, 0, 15, 15)
            t.ctext('EXIT', 2 if arrow else 6, (240, 250, 240))
            if arrow == 'left':
                t.hline(10, (240, 250, 240), 3, 12)
                t.set(4, 9, (240, 250, 240)); t.set(4, 11, (240, 250, 240)); t.set(5, 8, (240, 250, 240)); t.set(5, 12, (240, 250, 240))
            elif arrow == 'right':
                t.hline(10, (240, 250, 240), 3, 12)
                t.set(11, 9, (240, 250, 240)); t.set(11, 11, (240, 250, 240)); t.set(10, 8, (240, 250, 240)); t.set(10, 12, (240, 250, 240))
        return f
    add(sid, 'Exit Sign' + (f' ({k.title()})' if k else ''), 'light', 'plastic', {'self': exit_paint()}, boxes=[plate(1, 4, 15, 12, 1, 'self')], mount='wall', light=8, lightmode='always', collide=False)
add('uv_lamp', 'UV Lamp', 'light', 'glass', boxes=[ceiling(1, 5, 15, 11, 2, 'dgrey'), (2, 13.5, 6, 14, 14, 10, 'LAMP')], light=10, lamp='purple', **SW)
for k in ('white', 'red', 'blue'):
    add(f'{k}_light_strip', f'{k.title()} Light Strip', 'light', 'glass', boxes=[plate(0, 7, 16, 9, 1, 'LAMP')], mount='wall', light=9, lamp=k, collide=False, lightmode='always')
add('hanging_lamp', 'Hanging Lamp', 'light', 'metal', boxes=[(7.5, 10, 7.5, 8.5, 16, 8.5, 'black'), (4, 6, 4, 12, 10, 12, 'dgrey'), (6, 5, 6, 10, 6, 10, 'LAMP')], light=14, lamp='white', **SW)
add('broken_light', 'Broken Light', 'shaped', 'glass', {'self': P.with_overlay(P.panel('offwhite'), lambda t: (P.cracks(t, 3), t.fill((60, 60, 60), 4, 6, 9, 10)))},
    boxes=[ceiling(1, 5, 15, 11, 2, 'self'), (3, 12, 6, 8, 14, 9, 'self')], collide=False)
add('spotlight_rail', 'Spotlight Rail', 'light', 'metal', boxes=[ceiling(0, 7, 16, 9, 1, 'black'), (2, 11, 6, 6, 15, 10, 'black', 'LAMP'), (10, 11, 6, 14, 15, 10, 'black', 'LAMP')], light=12, lamp='white', collide=False, **SW)

# ======================= 4. Doors, windows and barriers =======================
add('heavy_containment_door', 'Heavy Containment Door', 'sliding_door', 'heavy',
    {'bottom': P.with_overlay(P.steel('dgrey'), lambda t: t.diag(shade('yellow', 1), shade('black', 1), 3, 0, 12, 16, 16)), 'top': P.with_overlay(P.steel('dgrey'), lambda t: t.ctext('SCP', 6, (230, 230, 230)))})
add('blast_door', 'Blast Door', 'sliding_door', 'heavy',
    {'bottom': P.with_overlay(P.steel('dsteel'), lambda t: t.diag(shade('yellow', 1), shade('black', 1), 4, 0, 0, 16, 5)), 'top': P.with_overlay(P.steel('dsteel'), lambda t: t.rect('black', 2, 2, 13, 13))})
add('office_door', 'Office Door', 'door', 'wood',
    {'bottom': P.with_overlay(P.planks((150, 110, 70), True), lambda t: t.fill('lgrey', 12, 0, 14, 2)), 'top': P.with_overlay(P.planks((150, 110, 70), True), lambda t: t.fill((200, 210, 215), 3, 3, 13, 12))}, set='OAK')
add('security_door', 'Security Door', 'door', 'heavy',
    {'bottom': P.with_overlay(P.panel('steel'), lambda t: t.hline(1, 'lgrey', 11, 13)), 'top': P.with_overlay(P.panel('steel'), lambda t: (t.fill((150, 170, 180), 5, 4, 11, 10), t.rect('dsteel', 4, 3, 11, 10)))}, set='IRON')
add('bulkhead_hatch', 'Bulkhead Hatch', 'trapdoor', 'heavy', {'all': P.with_overlay(P.steel('dsteel'), lambda t: t.circle(8, 8, 3, 'black', False))}, set='IRON')
add('floor_hatch', 'Floor Hatch', 'trapdoor', 'metal', {'all': P.with_overlay(P.diamond_plate('steel'), lambda t: t.hline(13, 'yellow', 5, 10))}, set='IRON')
add('observation_window', 'Observation Window', 'glass', 'glass', {'all': P.glass((60, 70, 80), 200, 'dgrey', tinted=True)}, variants=['pane'], translucent=True)
add('wired_glass', 'Wired Glass', 'glass', 'glass', {'all': P.glass(wires=True)}, variants=['pane'], translucent=True)
add('frosted_glass', 'Frosted Glass', 'glass', 'glass', {'all': P.glass((230, 236, 238), frost=True, frame='lgrey')}, variants=['pane'], translucent=True)
add('cracked_reinforced_glass', 'Cracked Reinforced Glass', 'glass', 'glass', {'all': P.glass(cracks=True, frame='dsteel')}, variants=['pane'], translucent=True)
add('security_bars', 'Security Bars', 'pane', 'heavy', {'all': P.bars((70, 72, 76))})
add('chain_link_fence', 'Chain-Link Fence', 'pane', 'metal', {'all': P.chain_link((170, 172, 175))})
add('barbed_wire', 'Barbed Wire', 'shaped', 'metal', {'self': P.decal((150, 150, 150), 'barbed')}, boxes=[(1, 0, 1, 15, 12, 15, 'self')], model='cross', collide=False, sticky=True)
add('turnstile', 'Turnstile', 'shaped', 'metal', boxes=[(0, 0, 5, 5, 13, 11, 'steel'), (5, 9, 7.5, 13, 10, 8.5, 'lgrey'), (5, 9, 7.5, 6, 10, 15, 'lgrey')])
add('boom_gate', 'Boom Gate', 'shaped', 'metal', {'self': P.stripes('red', 'white', 4)}, boxes=[(0, 0, 5, 5, 14, 11, 'yellow'), (5, 11, 7, 16, 13, 9, 'self')])
for k, c in dict(grey='concrete', orange='orange').items():
    add('jersey_barrier' if k == 'grey' else f'{k}_jersey_barrier', 'Jersey Barrier' if k == 'grey' else f'{k.title()} Jersey Barrier', 'shaped', 'stone',
        boxes=[(2, 0, 0, 14, 4, 16, c), (4, 4, 0, 12, 12, 16, c)])

# ======================= 5. Signs and markings =======================
W = dict(mount='wall', collide=False)
add('foundation_plaque', 'Foundation Plaque', 'shaped', 'metal', {'self': P.icon_only((40, 42, 46), (230, 230, 230), 'logo', 'lgrey')}, boxes=[plate(1, 1, 15, 15)], **W)
for k, c in dict(safe=(60, 150, 70), euclid=(220, 170, 40), keter=(170, 30, 30), thaumiel=(40, 40, 46)).items():
    add(f'{k}_class_plaque', f'{k.title()} Class Plaque', 'shaped', 'metal', {'self': P.sign(c, (240, 240, 240), [k[:4].upper()], 'lgrey', 'logo')}, boxes=[plate(2, 2, 14, 14)], **W)
add('site_sign', 'Site Sign', 'shaped', 'metal', {'self': P.sign((40, 42, 46), (230, 230, 230), ['SITE', '-19'])}, boxes=[plate(0, 2, 16, 14)], **W)
for k, (txt, c) in dict(light_containment=('LCZ', (210, 200, 160)), heavy_containment=('HCZ', (160, 70, 60)), entrance=('EZ', (90, 120, 160))).items():
    add(f'{k}_zone_sign', f'{k.replace("_", " ").title()} Zone Sign', 'shaped', 'metal', {'self': P.sign(c, (25, 25, 25), ['ZONE', txt])}, boxes=[plate(0, 3, 16, 13)], **W)
for k, (txt, ic) in dict(medical=('MED', 'cross'), cafeteria=('CAF', 'fork'), security=('SEC', 'shield'), research=('LAB', 'flask')).items():
    add(f'{k}_direction_sign', f'{k.title()} Sign', 'shaped', 'metal', {'self': P.sign((236, 234, 228), (40, 60, 120), [txt], (40, 60, 120), ic)}, boxes=[plate(1, 1, 15, 15)], **W)
for k, (ic, c) in dict(biohazard=('bio', 'yellow'), radiation=('trefoil', 'yellow'), electrical=('bolt', 'yellow'), memetic=('eye', (200, 60, 60)), cognitohazard=('brain', (200, 60, 60))).items():
    add(f'{k}_warning_sign', f'{k.title()} Warning Sign', 'shaped', 'metal', {'self': P.icon_only(c, 'black', ic, 'black')}, boxes=[plate(2, 2, 14, 14)], **W)
for n in range(1, 6):
    add(f'clearance_level_{n}_sign', f'Clearance Level {n} Sign', 'shaped', 'metal', {'self': P.sign((236, 234, 228), (30, 30, 30), ['LVL', f'{n}'], ((60, 150, 70), (60, 120, 200), (220, 170, 40), (220, 110, 40), (170, 30, 30))[n - 1])}, boxes=[plate(2, 2, 14, 14)], **W)
for n in range(10):
    add(f'room_number_{n}', f'Room Number {n}', 'decal_wall', 'paint', {'self': P.decal((30, 30, 30), f'digit:{n}')})
for k, words in dict(authorized='AUTH/ONLY', do_not_look='DONT/LOOK', keep_shut='KEEP/SHUT').items():
    add(f'{k}_stencil', {'authorized': 'Authorized Personnel Stencil', 'do_not_look': 'Do Not Look Away Stencil', 'keep_shut': 'Keep Door Shut Stencil'}[k], 'decal_wall', 'paint', {'self': P.decal((30, 30, 30), 'words:' + words)})
add('evacuation_map', 'Evacuation Map', 'shaped', 'paint', {'self': P.evac_map()}, boxes=[plate(1, 2, 15, 14)], **W)
add('notice_board', 'Notice Board', 'shaped', 'wood', {'self': P.board((170, 130, 90), cork=True)}, boxes=[plate(0, 2, 16, 14)], **W)
for i, (bg, fg, ic, word) in enumerate([((40, 60, 120), (230, 230, 230), 'logo', 'SCP'), ((170, 30, 30), (240, 240, 230), 'eye', 'LOOK'), ((230, 200, 60), (30, 30, 30), 'person', 'SAFE')], start=1):
    add(f'foundation_poster_{i}', f'Foundation Poster ({word.title()})', 'shaped', 'paint', {'self': P.poster(bg, fg, ic, word)}, boxes=[plate(2, 0, 14, 16, 0.5)], **W)
add('chaos_insurgency_tag', 'Chaos Insurgency Tag', 'decal_wall', 'paint', {'self': P.decal((40, 110, 50), 'tag')})
add('chaos_insurgency_scrawl', 'Chaos Insurgency Scrawl', 'decal_wall', 'paint', {'self': P.decal((40, 110, 50), 'tag2')})
add('blood_smear', 'Blood Smear', 'decal_wall', 'paint', {'self': P.decal((120, 10, 10), 'splat')})
add('blood_pool', 'Blood Pool', 'decal_floor', 'paint', {'self': P.decal((110, 8, 8), 'pool')})

# ======================= 6. Pipes, vents and utilities =======================
for k, c in dict(grey=(140, 142, 146), red=(170, 40, 36), blue=(50, 90, 170), yellow=(220, 180, 40)).items():
    add('pipe' if k == 'grey' else f'{k}_pipe', 'Pipe' if k == 'grey' else f'{k.title()} Pipe', 'axis', 'metal', {'self': P.ribs(c, 8)}, half=(3, 3))
add('pipe_valve', 'Pipe Valve', 'shaped', 'metal', boxes=[(5, 0, 5, 11, 16, 11, 'steel'), (2, 6, 2, 14, 10, 4, 'red'), (7, 7, 4, 9, 9, 5, 'dsteel')])
add('air_duct', 'Air Duct', 'pillar', 'metal', {'side': P.with_overlay(P.panel((176, 180, 184)), lambda t: (t.vline(4, shade((176, 180, 184), .8)), t.vline(11, shade((176, 180, 184), .8)))), 'end': P.vent((176, 180, 184))})
add('vent_cover', 'Vent Cover', 'trapdoor', 'metal', {'all': P.vent()}, set='IRON_HAND', cutout=False)
add('cable_tray', 'Cable Tray', 'axis', 'metal', {'self': P.with_overlay(P.panel((90, 92, 96)), lambda t: [t.vline(x, c) for x, c in ((5, (40, 40, 40)), (7, (150, 30, 30)), (9, (40, 60, 150)), (11, (200, 170, 40)))])}, half=(6, 2))
add('electrical_panel', 'Electrical Panel', 'shaped', 'metal', {'front': P.with_overlay(P.panel((150, 152, 150)), lambda t: P.ICONS['bolt'](t, 'yellow'))}, boxes=[(2, 2, 12, 14, 14, 16, 'steel', 'front')], mount='wall')
add('junction_box', 'Junction Box', 'shaped', 'metal', boxes=[(4, 4, 13, 12, 12, 16, 'lgrey'), (7, 12, 14, 9, 16, 16, 'dgrey')], mount='wall')
add('sprinkler', 'Sprinkler', 'shaped', 'metal', boxes=[(7, 13, 7, 9, 16, 9, 'copper'), (6, 12.5, 6, 10, 13, 10, 'copper')], collide=False)
add('fire_alarm', 'Fire Alarm', 'shaped', 'metal', {'front': P.sign('red', (240, 240, 240), ['PULL'])}, boxes=[(5, 4, 14, 11, 12, 16, 'red', 'front')], mount='wall')
add('fire_extinguisher', 'Fire Extinguisher', 'shaped', 'metal', boxes=[(5, 0, 9, 10, 11, 14, 'red'), (6, 11, 10, 9, 13, 13, 'black'), (9, 10, 11, 12, 11, 12, 'black')])
add('gas_canister_rack', 'Gas Canister Rack', 'shaped', 'metal', boxes=[(1, 0, 10, 5, 14, 14, 'green'), (6, 0, 10, 10, 14, 14, 'yellow'), (11, 0, 10, 15, 14, 14, 'blue'), (0, 8, 14, 16, 10, 16, 'dsteel')])
add('steam_vent', 'Steam Vent', 'shaped', 'metal', {'self': P.vent('dsteel')}, boxes=[(2, 0, 2, 14, 2, 14, 'self')], particle='CLOUD')
add('coolant_tank', 'Coolant Tank', 'tall', 'metal', {'self': P.with_overlay(P.ribs((120, 170, 200), 8), lambda t: t.rivets('lgrey', 1, 3))}, boxes=[(2, 0, 2, 14, 30, 14, 'self'), (6, 30, 6, 10, 32, 10, 'dsteel')])
add('transformer', 'Transformer', 'shaped', 'metal', {'front': P.with_overlay(P.ribs((110, 120, 110), 2), lambda t: P.ICONS['bolt'](t, 'yellow'))}, boxes=[(2, 0, 2, 14, 13, 14, 'olive', 'front'), (4, 13, 6, 6, 16, 8, 'white'), (10, 13, 6, 12, 16, 8, 'white')])

# ======================= 7. Security and surveillance =======================
add('security_camera', 'Security Camera', 'shaped', 'metal', boxes=[(6, 9, 13, 10, 13, 16, 'dgrey'), (7, 10, 9, 9, 12, 13, 'dgrey'), (5, 9, 2, 11, 13, 9, 'white', 'black')], mount='wall', collide=False)
add('ceiling_security_camera', 'Ceiling Security Camera', 'shaped', 'metal', boxes=[ceiling(6, 6, 10, 10, 1, 'dgrey'), (7, 12, 7, 9, 15, 9, 'dgrey'), (5, 9, 4, 11, 12, 12, 'white', 'black')], collide=False)
add('monitor_bank', 'Monitor Bank', 'facing', 'metal', {'front': P.with_overlay(P.solid('dgrey'), lambda t: [t.fill((60, 90, 80), x, y, x + 6, y + 6) for x in (1, 9) for y in (1, 9)]), 'side': P.panel('dgrey'), 'top': P.panel('dgrey')}, light=6)
add('motion_sensor', 'Motion Sensor', 'shaped', 'plastic', boxes=[(5, 11, 13, 11, 15, 16, 'white', 'black')], mount='wall', collide=False)
add('intercom', 'Intercom', 'shaped', 'metal', {'front': P.with_overlay(P.vent('steel'), lambda t: t.fill('red', 11, 12, 14, 14))}, boxes=[(4, 3, 14, 12, 13, 16, 'steel', 'front')], mount='wall', collide=False)
add('pa_speaker', 'PA Speaker', 'shaped', 'metal', boxes=[(7, 10, 13, 9, 12, 16, 'dgrey'), (5, 8, 8, 11, 14, 13, 'lgrey', 'black')], mount='wall', collide=False)
add('tesla_gate', 'Tesla Gate', 'light', 'heavy', boxes=[(3, 0, 3, 13, 3, 13, 'dgrey'), (5, 3, 5, 11, 16, 11, 'coil'), (4, 13, 4, 12, 16, 12, 'LAMP')], light=8, lamp='blue', lightmode='switch', particle='ELECTRIC_SPARK')
add('metal_detector', 'Metal Detector', 'tall', 'metal', boxes=[(0, 0, 5, 2, 32, 11, 'lgrey'), (14, 0, 5, 16, 32, 11, 'lgrey'), (0, 29, 5, 16, 32, 11, 'lgrey')], collide=True)
add('lockdown_button', 'Lockdown Button', 'shaped', 'metal', boxes=[(4, 4, 14, 12, 12, 16, 'yellow'), (6, 6, 12, 10, 10, 14, 'red')], mount='wall')
add('weapon_rack', 'Weapon Rack', 'shaped', 'wood', {'front': P.with_overlay(P.planks('dwood', False), lambda t: [t.vline(x, (30, 30, 30), 2, 13) for x in (3, 7, 11)])}, boxes=[(0, 1, 13, 16, 15, 16, 'dwood', 'front')], mount='wall')
add('riot_shield_rack', 'Riot Shield Rack', 'shaped', 'metal', boxes=[(0, 0, 14, 16, 2, 16, 'dsteel'), (1, 1, 12, 7, 15, 13, 'blue_glass'), (9, 1, 12, 15, 15, 13, 'blue_glass')], mount='wall')
add('guard_booth_window', 'Guard Booth Window', 'shaped', 'metal', boxes=[(0, 0, 0, 16, 8, 16, 'steel'), (0, 8, 7, 16, 16, 9, 'glass')], translucent=True)
add('sandbags', 'Sandbags', 'cube', 'cloth', {'all': P.with_overlay(P.cloth('khaki', False), lambda t: [t.hline(y, shade('khaki', .7)) for y in (0, 5, 10, 15)] + [t.vline(x, shade('khaki', .7), y, y + 4) for y, x in ((0, 7), (5, 3), (5, 12), (10, 8))])}, variants=SBW)

# ======================= 8. Labs and research =======================
add('lab_bench', 'Lab Bench', 'shaped', 'metal', {'front': P.drawers((220, 220, 216), 2)}, boxes=[(0, 0, 1, 16, 14, 16, 'white', 'front'), (0, 14, 0, 16, 16, 16, 'black')])
add('fume_hood', 'Fume Hood', 'tall', 'metal', {'front': P.drawers((220, 220, 216), 2)}, boxes=[(0, 0, 1, 16, 14, 16, 'white', 'front'), (0, 14, 0, 16, 15, 16, 'black'), (0, 15, 3, 16, 32, 16, 'white'), (1, 16, 2, 15, 28, 3, 'glass')], translucent=True)
add('microscope', 'Microscope', 'shaped', 'metal', boxes=[(5, 0, 5, 11, 1, 11, 'black'), (7, 1, 9, 9, 8, 11, 'white'), (6, 4, 5, 10, 5, 9, 'black'), (7, 6, 6, 9, 11, 8, 'white')])
for k, c in dict(eye=(240, 240, 230), tentacle=(140, 60, 120)).items():
    add(f'{k}_specimen_jar', f'Specimen Jar ({k.title()})', 'shaped', 'glass', {'self': P.jar((150, 200, 150), c)}, boxes=[(4, 0, 4, 12, 10, 12, 'self'), (5, 10, 5, 11, 11, 11, 'black')])
add('specimen_tank', 'Specimen Tank', 'tall', 'glass', {'self': P.jar((90, 200, 170), (200, 170, 160))}, boxes=[(1, 0, 1, 15, 3, 15, 'dsteel'), (2, 3, 2, 14, 29, 14, 'self'), (1, 29, 1, 15, 32, 15, 'dsteel')], light=10)
add('centrifuge', 'Centrifuge', 'shaped', 'metal', {'top': P.with_overlay(P.panel('white'), lambda t: t.circle(8, 8, 5, 'dgrey', False))}, boxes=[(2, 0, 2, 14, 8, 14, 'white'), (3, 8, 3, 13, 9, 13, 'lgrey')], toptex='top')
add('chemical_shelf', 'Chemical Shelf', 'shaped', 'metal', {'front': P.with_overlay(P.solid('dsteel'), lambda t: [t.fill(c, x, y, x + 2, y + 4) for y in (2, 9) for x, c in ((2, (200, 60, 60)), (5, (60, 160, 220)), (9, (220, 200, 60)), (12, (90, 200, 90)))])}, boxes=[(0, 0, 9, 16, 16, 16, 'steel', 'front')])
add('biohazard_bin', 'Biohazard Bin', 'shaped', 'plastic', {'front': P.icon_only('red', 'black', 'bio')}, boxes=[(3, 0, 3, 13, 12, 13, 'red', 'front'), (2.5, 12, 2.5, 13.5, 13, 13.5, 'red')])
add('whiteboard', 'Whiteboard', 'shaped', 'plastic', {'self': P.board((244, 244, 244), ink=(40, 70, 160))}, boxes=[plate(0, 2, 16, 14)], **W)
add('sample_case', 'Sample Case', 'shaped', 'glass', boxes=[(1, 0, 1, 15, 4, 15, 'dwood'), (2, 4, 2, 14, 14, 14, 'glass')], translucent=True)
add('decon_shower', 'Decontamination Shower', 'shaped', 'metal', boxes=[(7, 14, 7, 9, 16, 9, 'steel'), (4, 13, 4, 12, 14, 12, 'steel')], collide=False, particle='FALLING_WATER')
add('airlock_frame', 'Airlock Frame', 'shaped', 'heavy', {'self': P.with_overlay(P.panel('white'), lambda t: t.diag(shade('yellow', 1), shade('black', 1), 2, 0, 0, 16, 3))},
    boxes=[(0, 0, 0, 3, 16, 16, 'self'), (13, 0, 0, 16, 16, 16, 'self'), (3, 13, 0, 13, 16, 16, 'self')])
add('hazmat_locker', 'Hazmat Locker', 'tall', 'metal', {'front': P.with_overlay(P.locker('yellow'), lambda t: P.ICONS['bio'](t, 'black'))}, boxes=[(1, 0, 1, 15, 32, 15, 'yellow', 'front')])
add('eyewash_station', 'Eyewash Station', 'shaped', 'plastic', {'front': P.icon_only((60, 160, 80), (240, 240, 240), 'eye')}, boxes=[(4, 4, 12, 12, 12, 16, 'green', 'front')], mount='wall')

# ======================= 9. Computers and electronics =======================
add('server_rack', 'Server Rack', 'tall', 'metal', {'front': P.server_front()}, boxes=[(1, 0, 1, 15, 32, 15, 'black', 'front')], light=4)
add('crt_monitor', 'CRT Monitor', 'light', 'plastic', boxes=[(2, 0, 3, 14, 11, 15, (200, 196, 180), 'LAMP')], light=5, lamp='screen', lightmode='switch')
add('flat_monitor', 'Flat Monitor', 'light', 'plastic', boxes=[(5, 0, 8, 11, 1, 12, 'black'), (7, 1, 10, 9, 5, 11, 'black'), (1, 4, 9, 15, 13, 10, 'black', 'LAMP')], light=5, lamp='screen', lightmode='switch')
add('keyboard', 'Keyboard', 'shaped', 'plastic', {'top': P.with_overlay(P.solid((50, 50, 52)), lambda t: [t.set(x, y, (120, 120, 120)) for y in range(5, 11, 2) for x in range(2, 14, 2)])}, boxes=[(2, 0, 4, 14, 1, 10, 'black')], collide=False, toptex='top')
add('control_console', 'Control Console', 'shaped', 'metal', {'top': P.buttons()}, boxes=[(0, 0, 2, 16, 10, 16, 'dgrey'), (0, 10, 6, 16, 12, 16, 'dgrey')], toptex='top')
add('wall_terminal', 'Wall Terminal', 'light', 'metal', boxes=[(2, 3, 14, 14, 13, 16, 'dgrey', 'LAMP')], mount='wall', light=5, lamp='screen', lightmode='switch')
add('printer', 'Printer', 'shaped', 'plastic', boxes=[(2, 0, 3, 14, 6, 13, (200, 200, 196)), (4, 6, 9, 12, 9, 13, 'paper')])
add('tape_reel_computer', 'Tape Reel Computer', 'tall', 'metal', {'front': P.tape_reels()}, boxes=[(0, 0, 1, 16, 32, 16, (190, 186, 172), 'front')])
add('ruined_tape_reel_computer', 'Ruined Tape Reel Computer', 'tall', 'metal', {'front': P.with_overlay(P.tape_reels(), lambda t: (P.cracks(t, 3), t.speckle('rust', 30)))}, boxes=[(0, 0, 1, 16, 30, 16, 'rust', 'front')])
add('radio_set', 'Radio Set', 'shaped', 'metal', {'front': P.with_overlay(P.panel('olive'), lambda t: (t.circle(4, 8, 2.5, 'black'), t.circle(11, 8, 2.5, 'black'), t.fill((230, 200, 80), 2, 2, 14, 4)))}, boxes=[(2, 0, 5, 14, 7, 12, 'olive', 'front'), (12, 7, 9, 13, 14, 10, 'black')])
add('oscilloscope', 'Oscilloscope', 'shaped', 'metal', {'front': P.with_overlay(P.panel('lgrey'), lambda t: (t.fill('black', 2, 2, 10, 10), [t.set(x, 6 + int(2.5 * __import__('math').sin(x)), (90, 255, 120)) for x in range(2, 10)]))}, boxes=[(2, 0, 3, 14, 8, 14, 'lgrey', 'front')], light=3)
add('data_cabinet', 'Data Cabinet', 'facing', 'metal', {'front': P.drawers((150, 152, 156), 4), 'side': P.panel((150, 152, 156)), 'top': P.panel((150, 152, 156))})
add('broken_monitor', 'Broken Monitor', 'shaped', 'plastic', {'front': P.broken_screen()}, boxes=[(2, 0, 3, 14, 11, 15, (200, 196, 180), 'front')])

# ======================= 10. Offices and furniture =======================
add('office_desk', 'Office Desk', 'shaped', 'wood', boxes=[(0, 13, 0, 16, 16, 16, 'wood')] + legs(13, 1, 2, 'dsteel'))
add('director_desk', 'Director\'s Desk', 'shaped', 'wood', {'front': P.planks('dwood', True)}, boxes=[(0, 0, 1, 16, 14, 16, 'dwood', 'front'), (0, 14, 0, 16, 16, 16, 'wood')])
add('office_chair', 'Office Chair', 'shaped', 'cloth', boxes=[(7, 1, 7, 9, 6, 9, 'black'), (3, 0, 7, 13, 1, 9, 'black'), (7, 0, 3, 9, 1, 13, 'black'), (2, 6, 2, 14, 8, 14, 'cushion'), (2, 8, 12, 14, 16, 14, 'cushion')])
add('folding_chair', 'Folding Chair', 'shaped', 'metal', boxes=legs(7, 2, 1, 'dsteel') + [(2, 7, 2, 14, 8, 14, 'dsteel'), (2, 8, 13, 14, 16, 14, 'dsteel')])
add('filing_cabinet', 'Filing Cabinet', 'shaped', 'metal', {'front': P.drawers((140, 144, 146), 3)}, boxes=[(1, 0, 1, 15, 16, 15, (140, 144, 146), 'front')])
for k, c in dict(grey=(140, 144, 146), blue=(70, 100, 160)).items():
    add('locker' if k == 'grey' else f'{k}_locker', 'Locker' if k == 'grey' else f'{k.title()} Locker', 'tall', 'metal', {'front': P.locker(c)}, boxes=[(1, 0, 1, 15, 32, 15, c, 'front')])
add('binder_shelf', 'Binder Shelf', 'facing', 'wood', {'front': P.with_overlay(P.solid('dwood'), lambda t: [t.fill(c, x, y, x + 2, y + 6) for y in (1, 9) for x, c in zip(range(1, 15, 2), [(170, 40, 40), (40, 70, 150), (230, 230, 220), (40, 120, 60), (200, 170, 40), (40, 40, 40), (120, 60, 140)])]), 'side': P.planks('dwood'), 'top': P.planks('dwood')})
add('water_cooler', 'Water Cooler', 'tall', 'plastic', boxes=[(3, 0, 3, 13, 15, 13, 'white'), (4, 15, 4, 12, 26, 12, 'blue_glass')], translucent=True)
add('coffee_machine', 'Coffee Machine', 'shaped', 'metal', {'front': P.with_overlay(P.panel('black'), lambda t: (t.fill('steel', 5, 8, 11, 14), t.fill((240, 240, 240), 6, 11, 10, 14), t.set(3, 3, 'red')))}, boxes=[(3, 0, 4, 13, 12, 14, 'black', 'front')])
add('vending_machine', 'Vending Machine', 'tall', 'metal', {'front': P.vending()}, boxes=[(0, 0, 1, 16, 32, 16, 'red', 'front')], light=6)
for k, c in dict(fern=(60, 130, 50), ficus=(80, 150, 70)).items():
    add(f'potted_{k}', f'Potted {k.title()}', 'shaped', 'wood', {'self': P.decal(c, 'leaves')}, boxes=[(5, 0, 5, 11, 6, 11, 'terracotta'), (3, 6, 3, 13, 16, 13, 'self')], cutout=True, collide=True)
add('wall_clock', 'Wall Clock', 'shaped', 'plastic', {'front': P.clock()}, boxes=[(3, 3, 15, 13, 13, 16, 'black', 'front')], mount='wall', collide=False, cutout=True)
add('paper_stack', 'Paper Stack', 'shaped', 'paint', boxes=[(3, 0, 3, 13, 3, 13, 'paper')])
add('redacted_folders', 'Redacted Folders', 'shaped', 'paint', {'top': P.with_overlay(P.solid((220, 190, 120)), lambda t: (t.fill('black', 3, 5, 13, 7), t.fill('black', 3, 9, 10, 11), t.ctext('[  ]', 1, 'red')))}, boxes=[(2, 0, 3, 14, 4, 13, (220, 190, 120))], toptex='top')
add('conference_table', 'Conference Table', 'shaped', 'wood', boxes=[(0, 13, 0, 16, 16, 16, 'dwood'), (6, 0, 6, 10, 13, 10, 'black')])
for k, c in dict(brown='cloth_brown', blue='cloth_blue').items():
    add(f'{k}_couch', f'{k.title()} Couch', 'shaped', 'cloth', boxes=[(0, 0, 0, 16, 8, 16, c), (0, 8, 11, 16, 16, 16, c)])

# ======================= 11. Living quarters, cafeteria and medical =======================
add('bunk_bed', 'Bunk Bed', 'tall', 'metal', boxes=[(0, 0, 0, 2, 30, 2, 'dsteel'), (14, 0, 0, 16, 30, 2, 'dsteel'), (0, 0, 14, 2, 30, 16, 'dsteel'), (14, 0, 14, 16, 30, 16, 'dsteel'), (0, 4, 0, 16, 7, 16, 'mattress'), (0, 20, 0, 16, 23, 16, 'mattress')])
add('d_class_cot', 'D-Class Cot', 'shaped', 'metal', boxes=[(0, 5, 0, 16, 7, 16, 'mattress')] + legs(5, 0, 2, 'dsteel'))
add('steel_toilet', 'Steel Toilet', 'shaped', 'metal', boxes=[(4, 0, 3, 12, 6, 12, 'steel'), (3, 6, 3, 13, 7, 12, 'lgrey'), (3, 0, 12, 13, 13, 16, 'steel')])
add('shower_stall', 'Shower Stall', 'shaped', 'stone', {'self': P.tiles((236, 236, 232), (200, 200, 196), 4, 0.02)}, boxes=[(0, 0, 15, 16, 16, 16, 'self'), (7, 12, 11, 9, 13, 15, 'steel'), (6, 11, 10, 10, 12, 12, 'steel')], mount='wall')
add('cafeteria_table', 'Cafeteria Table', 'shaped', 'metal', boxes=[(0, 12, 0, 16, 14, 16, 'white'), (2, 0, 7, 14, 12, 9, 'dsteel')])
add('serving_counter', 'Serving Counter', 'shaped', 'metal', boxes=[(0, 0, 0, 16, 14, 16, 'steel'), (0, 14, 0, 16, 16, 16, 'lgrey')])
add('food_tray', 'Food Tray', 'shaped', 'plastic', {'top': P.with_overlay(P.panel((150, 160, 170)), lambda t: (t.fill((130, 120, 90), 2, 2, 7, 7), t.fill((200, 190, 160), 9, 2, 14, 7), t.fill((110, 140, 80), 2, 9, 14, 14)))}, boxes=[(3, 0, 4, 13, 1, 12, 'tray')], collide=False, toptex='top')
add('industrial_fridge', 'Industrial Fridge', 'tall', 'metal', {'front': P.with_overlay(P.panel('steel'), lambda t: (t.vline(12, 'dgrey', 2, 13), t.hline(15, 'dgrey')))}, boxes=[(0, 0, 1, 16, 32, 16, 'steel', 'front')])
add('hospital_bed', 'Hospital Bed', 'shaped', 'metal', boxes=[(0, 6, 0, 16, 9, 16, 'mattress'), (0, 9, 14, 16, 14, 16, 'steel'), (0, 9, 6, 1, 12, 14, 'steel'), (15, 9, 6, 16, 12, 14, 'steel')] + legs(6, 1, 1, 'dsteel'))
add('iv_stand', 'IV Stand', 'shaped', 'metal', boxes=[(4, 0, 4, 12, 1, 12, 'dsteel'), (7.5, 1, 7.5, 8.5, 16, 8.5, 'steel'), (6, 10, 7, 10, 15, 9, 'blue_glass')], translucent=True)
add('medical_cabinet', 'Medical Cabinet', 'shaped', 'metal', {'front': P.icon_only((236, 236, 232), 'red', 'cross')}, boxes=[(2, 2, 10, 14, 14, 16, 'white', 'front')], mount='wall')
add('privacy_curtain', 'Privacy Curtain', 'pane', 'cloth', {'all': P.cloth((150, 190, 190))})
add('gurney', 'Gurney', 'shaped', 'metal', boxes=[(0, 8, 0, 16, 10, 16, 'mattress')] + legs(8, 1, 1, 'steel'))
add('morgue_drawer', 'Morgue Drawer', 'facing', 'heavy', {'front': P.with_overlay(P.panel('steel'), lambda t: (t.rect('dsteel', 1, 1, 14, 14), t.hline(8, 'lgrey', 5, 10), t.fill('white', 6, 3, 10, 5))), 'side': P.panel('steel'), 'top': P.panel('steel')})

# ======================= 12. Containment props =======================
add('containment_locker', 'Containment Locker', 'facing', 'heavy', {'front': P.with_overlay(P.steel('dgrey'), lambda t: (t.fill('yellow', 3, 3, 13, 6), t.ctext('SCP', 3, 'black'), t.fill('lgrey', 11, 9, 13, 12))), 'side': P.steel('dgrey'), 'top': P.steel('dgrey')})
add('item_pedestal', 'Item Pedestal', 'shaped', 'stone', boxes=[(4, 0, 4, 12, 12, 12, 'white'), (3, 12, 3, 13, 14, 13, 'black')])
add('restraint_chair', 'Restraint Chair', 'shaped', 'metal', boxes=legs(7, 2, 1, 'dsteel') + [(2, 7, 2, 14, 9, 14, 'dsteel'), (2, 9, 13, 14, 16, 15, 'dsteel'), (2, 9, 2, 4, 11, 12, 'black'), (12, 9, 2, 14, 11, 12, 'black')])
add('hydraulic_press', 'Hydraulic Press', 'shaped', 'heavy', {'self': P.with_overlay(P.steel('dgrey'), lambda t: t.diag(shade('yellow', 1), shade('black', 1), 3, 0, 0, 16, 4))}, boxes=[(0, 0, 0, 16, 4, 16, 'self'), (5, 4, 5, 11, 16, 11, 'steel')])
add('acid_vat', 'Acid Vat', 'shaped', 'heavy', {}, boxes=[(0, 0, 0, 16, 12, 16, 'dsteel'), (1, 12, 1, 15, 12.5, 15, 'acid')], light=6)
add('incinerator_vent', 'Incinerator Vent', 'cube', 'heavy', {'all': P.with_overlay(P.solid((255, 140, 40)), lambda t: [t.hline(y, (60, 30, 20)) for y in range(0, 16, 3)])}, light=9)
add('field_emitter', 'Containment Field Emitter', 'tall', 'heavy', boxes=[(3, 0, 3, 13, 3, 13, 'dgrey'), (5, 3, 5, 11, 26, 11, 'steel'), (4, 26, 4, 12, 30, 12, 'glow_green')], light=10, particle='ELECTRIC_SPARK')
add('reality_anchor', 'Scranton Reality Anchor', 'tall', 'heavy', boxes=[(2, 0, 2, 14, 4, 14, 'dgrey'), (5, 4, 5, 11, 24, 11, 'steel'), (3, 10, 3, 13, 12, 13, 'copper'), (3, 18, 3, 13, 20, 13, 'copper'), (6, 24, 6, 10, 28, 10, 'lamp_blue')], light=8)
add('memetic_display', 'Memetic Kill Agent Display', 'shaped', 'metal', {'front': P.screen(True, 'static')}, boxes=[(1, 2, 14, 15, 14, 16, 'black', 'front')], mount='wall', light=6)
add('cell_plate', 'Cell Number Plate', 'shaped', 'metal', {'self': P.sign((40, 42, 46), (230, 230, 230), ['SCP', '---'], 'lgrey')}, boxes=[plate(3, 4, 13, 12)], **W)
add('observation_grille', 'Observation Grille', 'shaped', 'metal', {'front': P.vent('dsteel')}, boxes=[(3, 3, 15, 13, 13, 16, 'dsteel', 'front')], mount='wall', collide=False)
add('feeding_hatch', 'Feeding Hatch', 'trapdoor', 'heavy', {'all': P.with_overlay(P.steel('dgrey'), lambda t: t.hline(12, 'yellow', 4, 11))}, set='IRON')
add('shackle_ring', 'Shackle Ring', 'shaped', 'metal', boxes=[(6, 6, 14, 10, 10, 16, 'dsteel'), (6, 2, 12, 10, 3, 14, 'steel'), (6, 2, 12, 7, 7, 13, 'steel'), (9, 2, 12, 10, 7, 13, 'steel')], mount='wall', collide=False)
for k, c in dict(green=(90, 170, 60), black=(30, 28, 34)).items():
    add(f'{k}_bio_residue', f'{k.title()} Biological Residue', 'decal_floor', 'paint', {'self': P.decal(c, 'goo')})

# ======================= 13. Storage and logistics =======================
add('supply_crate', 'Supply Crate', 'cube', 'wood', {'all': P.crate('wood')})
add('metal_crate', 'Metal Crate', 'cube', 'metal', {'all': P.with_overlay(P.steel('olive'), lambda t: t.ctext('SCP', 6, (230, 230, 230)))})
for k, c in dict(red=(150, 50, 40), blue=(50, 80, 140), green=(60, 110, 70)).items():
    add(f'{k}_shipping_container', f'{k.title()} Shipping Container', 'cube', 'metal', {'all': P.shipping(c)})
add('pallet', 'Pallet', 'shaped', 'wood', {'top': P.planks('tan', False)}, boxes=[(0, 0, 0, 16, 2, 16, 'wood')], toptex='top')
add('boxed_pallet', 'Pallet of Boxes', 'shaped', 'wood', {'self': P.with_overlay(P.solid((190, 160, 110)), lambda t: (t.hline(7, (160, 130, 90)), t.vline(7, (160, 130, 90))))}, boxes=[(0, 0, 0, 16, 2, 16, 'wood'), (1, 2, 1, 15, 15, 15, 'self')])
add('steel_shelving', 'Steel Shelving', 'shaped', 'metal', {'self': P.with_overlay(P.solid((190, 160, 110)), lambda t: t.hline(7, (160, 130, 90)))},
    boxes=legs(16, 0, 1, 'dsteel') + [(0, 0, 0, 16, 1, 16, 'dsteel'), (0, 7, 0, 16, 8, 16, 'dsteel'), (0, 15, 0, 16, 16, 16, 'dsteel'), (2, 1, 2, 9, 6, 9, 'self'), (8, 8, 6, 14, 13, 13, 'self')])
for k, c in dict(yellow='yellow', blue='blue').items():
    add(f'{k}_chemical_barrel', f'{k.title()} Chemical Barrel', 'shaped', 'metal', {'self': P.ribs(c, 5)}, boxes=[(2, 0, 2, 14, 16, 14, 'self')])
add('toxic_waste_barrel', 'Toxic Waste Barrel', 'shaped', 'metal', {'self': P.with_overlay(P.ribs((200, 170, 40), 5), lambda t: (P.ICONS['trefoil'](t, 'black'), t.fill((110, 230, 60), 4, 12, 9, 16)))}, boxes=[(2, 0, 2, 14, 16, 14, 'self')], light=5)
add('hand_truck', 'Hand Truck', 'shaped', 'metal', boxes=[(3, 0, 12, 4, 14, 13, 'red'), (12, 0, 12, 13, 14, 13, 'red'), (3, 0, 6, 13, 1, 13, 'dsteel'), (2, 0, 13, 4, 3, 15, 'black'), (12, 0, 13, 14, 3, 15, 'black')], collide=False)
add('forklift_prop', 'Forklift', 'shaped', 'metal', boxes=[(1, 0, 4, 15, 9, 16, 'yellow'), (2, 9, 8, 14, 16, 16, 'black'), (3, 0, 0, 5, 1, 4, 'dsteel'), (11, 0, 0, 13, 1, 4, 'dsteel'), (2, 1, 3, 14, 16, 4, 'dsteel')])

# ======================= 14. Ruins and aftermath =======================
add('rubble', 'Rubble', 'layer', 'rubble', {'all': P.rubble()})
add('collapsed_ceiling', 'Collapsed Ceiling', 'shaped', 'plastic', {'self': P.stained(P.speckle((232, 230, 224), [(200, 198, 190)], 25))},
    boxes=[ceiling(0, 0, 16, 16, 2, 'self'), (2, 9, 4, 9, 14, 5, 'self'), (10, 10, 9, 11, 14, 14, 'black')], collide=False)
add('sparking_wires', 'Sparking Wires', 'shaped', 'metal', {'self': P.decal((0, 0, 0), 'wires')}, boxes=[(2, 0, 2, 14, 16, 14, 'self')], model='cross', collide=False, particle='ELECTRIC_SPARK', light=3)
add('overgrown_facility_wall', 'Overgrown Facility Wall', 'cube', 'stone', {'all': P.overgrown(P.panel(WHITE_WALL, 0.03))}, variants=SB)
add('water_damaged_floor', 'Water-Damaged Floor', 'cube', 'stone', {'all': P.stained(P.tiles((210, 198, 199), (180, 170, 170), 2), (90, 100, 90), 5)}, variants=['slab'])
add('bullet_holed_wall', 'Bullet-Holed Wall', 'cube', 'stone', {'all': P.with_overlay(P.panel(WHITE_WALL, 0.03), P.holes)})
add('burn_mark', 'Burn Mark', 'decal_wall', 'paint', {'self': P.decal((0, 0, 0), 'scorch')})
add('scorch_mark', 'Scorch Mark', 'decal_floor', 'paint', {'self': P.decal((0, 0, 0), 'scorch')})
add('shattered_glass', 'Shattered Glass', 'decal_floor', 'glass', {'self': P.decal((0, 0, 0), 'shards')})
add('corroded_pipe', 'Corroded Pipe', 'axis', 'metal', {'self': P.with_overlay(P.ribs((140, 142, 146), 8), lambda t: t.speckle('rust', 60))}, half=(3, 3), particle='DRIPPING_WATER')
add('abandoned_paperwork', 'Abandoned Paperwork', 'decal_floor', 'paint', {'self': P.decal((0, 0, 0), 'papers')})

# ======================= 15. Surface and exterior =======================
add('asphalt', 'Asphalt', 'cube', 'stone', {'all': P.asphalt()}, variants=SB)
add('asphalt_line', 'Asphalt Road Line', 'cube', 'stone', {'all': P.asphalt('yellow')}, variants=['slab'])
add('concrete_paver', 'Concrete Paver', 'cube', 'stone', {'all': P.pavers((170, 168, 160))}, variants=SB)
for k, cs in dict(green=((60, 80, 40), (90, 110, 60), (50, 60, 35)), desert=((170, 150, 110), (190, 170, 130), (140, 120, 90))).items():
    add(f'{k}_camo_netting', f'{k.title()} Camo Netting', 'pane', 'cloth', {'all': P.camo(*cs)})
add('tower_railing', 'Tower Railing', 'pane', 'metal', {'all': P.with_overlay(P.bars('yellow', 8), lambda t: (t.hline(7, 'yellow'), t.hline(1, 'yellow')))})
add('catwalk', 'Catwalk', 'glass', 'metal', {'all': P.grate('steel')}, variants=['slab', 'stairs'], cutout=True)
add('steel_ladder', 'Steel Ladder', 'ladder', 'metal', {'all': P.with_overlay(lambda t: t.clear(), lambda t: (t.vline(2, 'steel'), t.vline(13, 'steel'), [t.hline(y, 'lgrey', 3, 12) for y in (1, 5, 9, 13)]))})
add('helipad_marking', 'Helipad Marking', 'cube', 'stone', {'all': P.helipad()})
add('front_company_sign', 'Front Company Sign', 'shaped', 'metal', {'self': P.sign((236, 234, 228), (40, 60, 120), ['ACME', 'STOR'], (40, 60, 120))}, boxes=[plate(0, 3, 16, 13)], **W)
add('antenna_mast', 'Antenna Mast', 'tall', 'metal', boxes=[(3, 0, 3, 13, 2, 13, 'dgrey'), (7, 2, 7, 9, 32, 9, 'steel'), (4, 22, 4, 12, 23, 12, 'white'), (5, 23, 10, 11, 29, 11, 'white')])
add('ventilation_stack', 'Ventilation Stack', 'tall', 'metal', {'self': P.ribs((150, 154, 158), 4)}, boxes=[(3, 0, 3, 13, 24, 13, 'self'), (2, 24, 2, 14, 27, 14, 'dgrey')], particle='SMOKE')
