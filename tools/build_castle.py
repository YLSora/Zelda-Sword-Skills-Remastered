"""Build and validate a 216 x 96 x 216 Minecraft 1.20.1 royal estate.

Dependencies: python -m pip install --target build/castle-tools nbtlib Pillow
Run from the repository root: python tools/build_castle.py
"""

from __future__ import annotations

import argparse
from collections import Counter, deque
from dataclasses import dataclass
import hashlib
import json
import math
from pathlib import Path
import sys
import zipfile

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/castle-tools"))

import nbtlib
import numpy as np
from nbtlib import ByteArray, Compound, File, Int, List, Short, String
from PIL import Image, ImageDraw, ImageFont

WIDTH, HEIGHT, LENGTH = 216, 96, 216
OFFSET_X, OFFSET_Z = 76, 76
X_MIN, X_MAX = -OFFSET_X, WIDTH - OFFSET_X - 1
Z_MIN, Z_MAX = -OFFSET_Z, LENGTH - OFFSET_Z - 1
MOAT_WIDTH = 8
DATA_VERSION = 3465
OUT = ROOT / "artifacts/royal_main_hall"
NAME = "royal_estate_216x96x216"
TOWERS = ((5, 5), (58, 5), (5, 58), (58, 58))
ANNEXES = ((-18, "banquet"), (81, "library"))
PREVIOUS_GOLD_BLOCKS = 1093
WALL_AXES = (87, 82)
GATE_Z = -51
MAIN_LIFT, TERRACE_LIFT = 5, 3
FOUNTAIN = (31.5, -27)
FORTS = tuple((round(31.5 + WALL_AXES[0] * math.cos(a)), round(31.5 + WALL_AXES[1] * math.sin(a)))
             for a in (0, math.pi / 4, 3 * math.pi / 4, math.pi, 5 * math.pi / 4, 7 * math.pi / 4))


@dataclass(frozen=True)
class Material:
    name: str
    legacy_id: int
    legacy_data: int
    color: tuple[int, int, int]
    properties: tuple[tuple[str, str], ...] = ()

    def tag(self):
        result = Compound({"Name": String("minecraft:" + self.name)})
        if self.properties:
            result["Properties"] = Compound({k: String(v) for k, v in self.properties})
        return result


MATERIALS: list[Material] = []


def material(name, legacy_id, data, color, **properties):
    value = Material(name, legacy_id, data, color, tuple(sorted(properties.items())))
    if value not in MATERIALS:
        MATERIALS.append(value)
    return MATERIALS.index(value)


AIR = material("air", 0, 0, (245, 248, 251))
STONE = material("stone", 1, 0, (113, 117, 121))
BRICK = material("stone_bricks", 98, 0, (153, 156, 156))
MOSS = material("mossy_stone_bricks", 98, 1, (125, 137, 109))
CRACK = material("cracked_stone_bricks", 98, 2, (141, 143, 141))
CHISEL = material("chiseled_stone_bricks", 98, 3, (159, 163, 165))
ANDESITE = material("polished_andesite", 1, 6, (136, 145, 150))
QUARTZ = material("quartz_block", 155, 0, (231, 225, 213))
PILLAR = material("quartz_pillar", 155, 2, (236, 232, 219), axis="y")
GOLD = material("gold_block", 41, 0, (229, 182, 48))
ROOF = material("dark_oak_planks", 5, 5, (65, 43, 25))
ROOF_EDGE = material("dark_oak_log", 162, 1, (46, 35, 23), axis="y")
TIMBER = material("spruce_planks", 5, 1, (105, 77, 49))
LOG = material("spruce_log", 17, 1, (78, 63, 44), axis="y")
GRASS = material("grass_block", 2, 0, (85, 124, 63), snowy="false")
DIRT = material("dirt", 3, 0, (116, 86, 62))
WATER = material("water", 9, 0, (49, 117, 158), level="0")
BLUE_GLASS = material("blue_stained_glass", 95, 11, (61, 99, 151))
LIGHT_GLASS = material("light_blue_stained_glass", 95, 3, (108, 177, 207))
GLOW = material("glowstone", 89, 0, (238, 202, 111))
RED = material("red_wool", 35, 14, (158, 37, 44))
YELLOW = material("yellow_wool", 35, 4, (239, 190, 54))
CARPET = material("red_carpet", 171, 14, (169, 33, 40))
GOLD_CARPET = material("yellow_carpet", 171, 4, (234, 188, 48))
FENCE = material("nether_brick_fence", 113, 0, (76, 58, 61),
                 north="false", south="false", east="false", west="false", waterlogged="false")
IRON = material("iron_bars", 101, 0, (146, 153, 157),
                north="false", south="false", east="false", west="false", waterlogged="false")
SLAB = material("stone_brick_slab", 44, 5, (162, 166, 167), type="bottom", waterlogged="false")
QUARTZ_SLAB = material("quartz_slab", 44, 7, (237, 231, 218), type="bottom", waterlogged="false")
STEP_SUPPORT = material("stone_brick_slab", 44, 13, (162, 166, 167), type="top", waterlogged="false")
TABLE_TOP = material("dark_oak_slab", 126, 5, (65, 43, 25), type="bottom", waterlogged="false")
BOOKSHELF = material("bookshelf", 47, 0, (137, 108, 72))
HAY = material("hay_block", 170, 0, (186, 157, 55), axis="y")
WHITE = material("white_wool", 35, 0, (228, 229, 225))
SAND = material("sand", 12, 0, (215, 204, 155))
COARSE_DIRT = material("coarse_dirt", 3, 1, (128, 94, 61))
CHEST = material("chest", 54, 2, (150, 103, 46), facing="north", type="single", waterlogged="false")
WOOD_FENCE = material("spruce_fence", 188, 0, (105, 77, 49),
                      north="false", south="false", east="false", west="false", waterlogged="false")
STALL_GATE = material("spruce_fence_gate", 183, 2, (105, 77, 49), facing="north", open="false", in_wall="false", powered="false")
FALLING_WATER = material("water", 8, 8, (64, 133, 176), level="8")
OAK_LOG = material("oak_log", 17, 0, (102, 80, 48), axis="y")
BIRCH_LOG = material("birch_log", 17, 2, (205, 206, 186), axis="y")
OAK_LEAVES = material("oak_leaves", 18, 4, (66, 117, 48), persistent="true", distance="1", waterlogged="false")
BIRCH_LEAVES = material("birch_leaves", 18, 6, (96, 137, 57), persistent="true", distance="1", waterlogged="false")
FLOWERS = (
    material("poppy", 38, 0, (207, 52, 51)),
    material("dandelion", 37, 0, (243, 206, 62)),
    material("allium", 38, 2, (173, 114, 192)),
    material("azure_bluet", 38, 3, (225, 234, 221)),
    material("oxeye_daisy", 38, 8, (247, 241, 203)),
)
SHORT_GRASS = material("grass", 31, 1, (103, 147, 65))
FERN = material("fern", 31, 2, (73, 128, 53))
PLANTS = {*FLOWERS, SHORT_GRASS, FERN}
STAIRS = {
    face: material("stone_brick_stairs", 109, data, (157, 160, 160),
                   facing=face, half="bottom", shape="straight", waterlogged="false")
    for face, data in (("east", 0), ("west", 1), ("south", 2), ("north", 3))
}
THRONE = material("quartz_stairs", 156, 2, (239, 226, 190),
                  facing="south", half="bottom", shape="straight", waterlogged="false")
SEATS = {
    face: material("spruce_stairs", 134, data, (105, 77, 49),
                   facing=face, half="bottom", shape="straight", waterlogged="false")
    for face, data in (("east", 0), ("west", 1), ("south", 2), ("north", 3))
}
LADDER = material("ladder", 65, 5, (164, 123, 67), facing="east", waterlogged="false")
BANNERS = {
    face: material("red_wall_banner", 177, data, (170, 35, 44), facing=face)
    for face, data in (("north", 2), ("south", 3), ("west", 4), ("east", 5))
}


def octagon(x, z, half=24, diagonal=34):
    dx, dz = abs(x - 31.5), abs(z - 31.5)
    return max(dx, dz) <= half and dx + dz <= diagonal


class Castle:
    def __init__(self):
        # Flattening this array gives MCEdit's x + z*width + y*width*length order.
        self.blocks = np.zeros((HEIGHT, LENGTH, WIDTH), dtype=np.uint16)
        self.banner_positions = {}
        self.bridge_deck = {}
        self.tree_positions = []
        self.garden_beds = []
        self.courtyard_paths = set()
        self.wall_distance = None
        self.ground = np.full((LENGTH, WIDTH), 4, dtype=np.int16)
        self.palace_lift = np.zeros((LENGTH, WIDTH), dtype=np.int16)
        self.chest_positions = set()
        self.terrain_cells = set()

    def set(self, x, y, z, state):
        assert X_MIN <= x <= X_MAX and 0 <= y < HEIGHT and Z_MIN <= z <= Z_MAX, (x, y, z)
        self.blocks[y, z + OFFSET_Z, x + OFFSET_X] = state

    def get(self, x, y, z):
        assert X_MIN <= x <= X_MAX and 0 <= y < HEIGHT and Z_MIN <= z <= Z_MAX, (x, y, z)
        return int(self.blocks[y, z + OFFSET_Z, x + OFFSET_X])

    def box(self, x1, y1, z1, x2, y2, z2, state):
        assert X_MIN <= x1 <= x2 <= X_MAX and 0 <= y1 <= y2 < HEIGHT and Z_MIN <= z1 <= z2 <= Z_MAX
        self.blocks[y1:y2 + 1, z1 + OFFSET_Z:z2 + OFFSET_Z + 1,
                    x1 + OFFSET_X:x2 + OFFSET_X + 1] = state

    def stone(self, x, y, z):
        value = (x * 59 + y * 31 + z * 83) % 101
        if y < 8 and value < 12:
            return MOSS
        return CRACK if value < 8 else BRICK

    def build(self):
        self.foundation()
        self.fortifications()
        self.hall()
        self.windows()
        self.towers()
        self.roof()
        self.entrance()
        self.bridge()
        self.open_second_floor()
        self.tower_entrances()
        self.staircases()
        self.throne()
        self.flags()
        self.lighting()
        self.annexes()
        self.annex_connections()
        self.raise_palace()
        self.service_buildings()
        self.training_yard()
        self.fountain()
        self.courtyard_roads()
        self.rolling_terrain()
        self.gardens()
        self.connect_rails()
        return self

    def foundation(self):
        # A sampled ellipse supplies an even-width distance band, including
        # the rounded projections of the six bastions, for the moat and banks.
        xx, zz = np.meshgrid(np.arange(X_MIN, X_MAX + 1), np.arange(Z_MIN, Z_MAX + 1))
        distance_sq = np.full(xx.shape, np.inf)
        for angle in np.linspace(0, math.tau, 1440, endpoint=False):
            px, pz = 31.5 + WALL_AXES[0] * math.cos(angle), 31.5 + WALL_AXES[1] * math.sin(angle)
            distance_sq = np.minimum(distance_sq, (xx - px) ** 2 + (zz - pz) ** 2)
        inside = ((xx - 31.5) / WALL_AXES[0]) ** 2 + ((zz - 31.5) / WALL_AXES[1]) ** 2 < 1
        self.wall_distance = np.sqrt(distance_sq) * np.where(inside, -1, 1)
        shoreline = self.wall_distance - 2.5
        for cx, cz in FORTS:
            shoreline = np.minimum(shoreline, np.hypot(xx - cx, zz - cz) - 5.5)
        self.shoreline = shoreline
        self.box(X_MIN, 0, Z_MIN, X_MAX, 0, Z_MAX, STONE)
        self.box(X_MIN, 1, Z_MIN, X_MAX, 3, Z_MAX, DIRT)
        self.box(X_MIN, 4, Z_MIN, X_MAX, 4, Z_MAX, GRASS)
        for x in range(X_MIN, X_MAX + 1):
            for z in range(Z_MIN, Z_MAX + 1):
                distance = shoreline[z + OFFSET_Z, x + OFFSET_X]
                if 2 < distance <= 2 + MOAT_WIDTH:
                    self.box(x, 1, z, x, 3, z, WATER)
                    self.set(x, 4, z, AIR)
                elif 1 < distance <= 2 or 2 + MOAT_WIDTH < distance <= 3 + MOAT_WIDTH:
                    self.box(x, 1, z, x, 3, z, STONE)
                    self.set(x, 4, z, BRICK)

    def fortifications(self):
        for z in range(Z_MIN, Z_MAX + 1):
            for x in range(X_MIN, X_MAX + 1):
                d = self.wall_distance[z + OFFSET_Z, x + OFFSET_X]
                if abs(d) > 2.5:
                    continue
                for y in range(1, 15):
                    self.set(x, y, z, CHISEL if y in (4, 12) else self.stone(x, y, z))
                self.set(x, 14, z, ANDESITE)
                if abs(d) >= 1.5:
                    self.set(x, 15, z, QUARTZ)
                    angle = math.atan2((z - 31.5) / WALL_AXES[1], (x - 31.5) / WALL_AXES[0])
                    if int((angle + math.pi) * 22) % 3 != 1:
                        self.set(x, 16, z, BRICK)
                if abs(d) < 0.5 and (x * 5 + z * 3) % 19 == 0:
                    self.set(x, 14, z, GLOW)
        for cx, cz in FORTS:
            for x in range(cx - 5, cx + 6):
                for z in range(cz - 5, cz + 6):
                    r = math.hypot(x - cx, z - cz)
                    if r > 5.5:
                        continue
                    self.box(x, 1, z, x, 4, z, BRICK)
                    for y in range(5, 23):
                        state = self.stone(x, y, z) if r >= 4 else AIR
                        if y in (14, 22):
                            state = ANDESITE
                        if r >= 4 and y in (12, 21):
                            state = QUARTZ
                        self.set(x, y, z, state)
                    if r >= 4.2:
                        self.set(x, 23, z, CHISEL)
                        if (x + z) % 3 != 0:
                            self.set(x, 24, z, BRICK)
                    # Wall-walk openings follow the original curve through
                    # each bastion, preserving one continuous patrol circuit.
                    if abs(self.wall_distance[z + OFFSET_Z, x + OFFSET_X]) < 1.5:
                        self.box(x, 15, z, x, 18, z, AIR)
            direction = 1 if cx < 32 else -1
            self.box(min(cx, cx + direction * 8), 5, cz - 1,
                     max(cx, cx + direction * 8), 8, cz + 1, AIR)
            self.box(min(cx, cx + direction * 8), 4, cz - 1,
                     max(cx, cx + direction * 8), 4, cz + 1, ANDESITE)
            for y in range(5, 23):
                self.set(cx - 4, y, cz + 2, BRICK)
                self.set(cx - 3, y, cz + 2, LADDER)
            for y in (4, 14, 22):
                for dx, dz in ((2, 2), (2, -2), (-2, -2)):
                    self.set(cx + dx, y, cz + dz, GLOW)
        # The only external entrance is a broad open gateway on the main axis.
        self.box(24, 5, GATE_Z - 3, 39, 14, GATE_Z + 3, BRICK)
        for x in range(24, 40):
            for z in range(GATE_Z - 3, GATE_Z + 4):
                self.set(x, 14, z, ANDESITE)
                if z in (GATE_Z - 3, GATE_Z + 3):
                    self.set(x, 15, z, QUARTZ)
                    if x % 3 != 0:
                        self.set(x, 16, z, CHISEL)
            if 27 <= x <= 36:
                top = 13 - math.floor(abs(x - 31.5) / 2)
                self.box(x, 5, GATE_Z - 3, x, top, GATE_Z + 3, AIR)
                self.set(x, top + 1, GATE_Z - 3, QUARTZ)
        for x in (25, 38):
            self.box(x, 5, GATE_Z - 4, x, 14, GATE_Z - 4, PILLAR)
            self.set(x, 13, GATE_Z - 5, GLOW)
        # Two open stair flights ascend from the forecourt to the wall walk.
        for x1 in (19, 42):
            for step in range(1, 11):
                y, z = 4 + step, GATE_Z + 15 - step
                for x in range(x1, x1 + 3):
                    self.set(x, y, z, STAIRS["north"])
                    if step > 1:
                        self.set(x, y - 1, z, STEP_SUPPORT)
                    self.box(x, y + 1, z, x, y + 3, z, AIR)
            self.box(x1, 14, GATE_Z, x1 + 2, 14, GATE_Z + 4, ANDESITE)
            self.box(x1, 15, GATE_Z, x1 + 2, 18, GATE_Z + 4, AIR)

    def bridge(self):
        # A three-block rise gives the wide stone bridge a gentle arch, with
        # oriented stair treads on both slopes and a continuous waterway below.
        for z in range(Z_MIN, GATE_Z + 8):
            phase = (z - (GATE_Z - 15)) / 14
            self.bridge_deck[z] = 4 + (round(3 * math.sin(math.pi * phase)) if 0 <= phase <= 1 else 0)
        for z, y in self.bridge_deck.items():
            previous = self.bridge_deck.get(z - 1, y)
            following = self.bridge_deck.get(z + 1, y)
            tread = STAIRS["south"] if y > previous else STAIRS["north"] if y > following else BRICK
            self.box(26, y - 1, z, 37, y, z, BRICK)
            self.box(27, y, z, 36, y, z, tread)
            self.box(27, y + 1, z, 36, y + 4, z, AIR)
            for x in (26, 37):
                if z > GATE_Z - 4:
                    continue
                self.set(x, y + 1, z, CHISEL)
                self.set(x, y + 2, z, FENCE)
                if z in (GATE_Z - 19, GATE_Z - 13, GATE_Z - 7):
                    self.set(x, y + 2, z, GLOW)
                    self.set(x, y + 3, z, SLAB)

    def hall(self):
        for x in range(8, 56):
            for z in range(8, 56):
                if not octagon(x, z):
                    continue
                radius = math.hypot(x - 31.5, z - 31.5)
                floor = ANDESITE
                if radius < 18.5:
                    floor = QUARTZ if (x // 3 + z // 3) % 2 == 0 else ANDESITE
                    if 16.9 < radius < 17.8 or 12.8 < radius < 13.5:
                        floor = ROOF_EDGE
                    if radius < 11 and (abs(x - 31.5) < 1 or abs(z - 31.5) < 1):
                        floor = BRICK
                self.set(x, 4, z, floor)
                shell = not octagon(x, z, 22, 32)
                if shell:
                    for y in range(5, 38):
                        self.set(x, y, z, self.stone(x, y, z))
                    for y in (5, 14, 25, 36, 37):
                        self.set(x, y, z, QUARTZ if y in (14, 25) else CHISEL)
                elif radius >= 16:
                    for y in (15, 26):
                        self.set(x, y - 1, z, BRICK)
                        self.set(x, y, z, TIMBER if radius < 21 else ANDESITE)
                        is_edge = any(math.hypot(x + dx - 31.5, z + dz - 31.5) < 16
                                      for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)))
                        if is_edge:
                            self.set(x, y, z, QUARTZ)
                            self.set(x, y + 1, z, FENCE)
        # Slender columns carry the galleries without enclosing them.
        for index in range(12):
            a = math.radians(15 + index * 30)
            x, z = round(31.5 + 19 * math.cos(a)), round(31.5 + 19 * math.sin(a))
            self.box(x, 5, z, x, 35, z, PILLAR)
            for y in (5, 13, 16, 24, 27, 35):
                self.box(x - 1, y, z - 1, x + 1, y, z + 1, CHISEL)
            for y in (12, 23, 34):
                for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    self.set(x + dx, y, z + dz, QUARTZ_SLAB)
        # Repeated exterior buttresses and projecting cornices.
        for side in range(4):
            for u in (23, 40):
                for y in range(5, 37):
                    depth = 2 if y < 16 else 1
                    for d in range(depth):
                        x, z = self.side(side, u, 7 - d)
                        self.set(x, y, z, CHISEL if y % 7 == 0 else BRICK)
                x, z = self.side(side, u, 7)
                self.set(x, 37, z, QUARTZ)
                self.set(x, 38, z, QUARTZ)

    @staticmethod
    def side(side, u, v):
        return ((u, v), (63 - v, u), (63 - u, 63 - v), (v, 63 - u))[side]

    def windows(self):
        for side in range(4):
            for u in (27, 36):
                for base in (7, 18, 29):
                    if side == 0 and base == 7:
                        continue
                    for du in range(-2, 3):
                        height = 6 - max(0, abs(du) - 0)
                        for dy in range(height):
                            for depth in (8, 9):
                                x, z = self.side(side, u + du, depth)
                                state = PILLAR if du == 0 else (LIGHT_GLASS if dy % 3 else BLUE_GLASS)
                                if abs(du) == 2 or dy == height - 1:
                                    state = QUARTZ
                                self.set(x, base + dy, z, state)
                    for du in range(-2, 3):
                        x, z = self.side(side, u + du, 7)
                        self.set(x, base - 1, z, QUARTZ_SLAB)

    def towers(self):
        for cx, cz in TOWERS:
            top = 33
            for x in range(cx - 5, cx + 6):
                for z in range(cz - 5, cz + 6):
                    radius = math.hypot(x - cx, z - cz)
                    if radius > 5.5:
                        continue
                    self.box(x, 1, z, x, 4, z, BRICK)
                    for y in range(5, top + 1):
                        state = self.stone(x, y, z) if radius >= 3.8 else AIR
                        if y in (14, 25, top - 1) and radius >= 3.8:
                            state = QUARTZ
                        if y in (15, 26, top) and radius < 3.8:
                            state = TIMBER
                        self.set(x, y, z, state)
                    if radius >= 4.3:
                        self.set(x, top + 1, z, CHISEL)
                        if (x + z) % 3 == 0:
                            self.set(x, top + 2, z, BRICK)
            for base in (8, 19, 28):
                for dx, dz in ((0, -5), (0, 5), (-5, 0), (5, 0)):
                    for y in range(base, min(base + 5, top)):
                        self.set(cx + dx, y, cz + dz, LIGHT_GLASS if y % 2 else BLUE_GLASS)
                        self.set(cx + dx - (1 if dx > 0 else -1 if dx < 0 else 0), y,
                                 cz + dz - (1 if dz > 0 else -1 if dz < 0 else 0), LIGHT_GLASS)
            # Ladders open through each landing, backed by the western wall.
            for y in range(5, top + 1):
                self.set(cx - 4, y, cz, BRICK)
                self.set(cx - 3, y, cz, LADDER)
                self.set(cx - 2, y, cz, AIR)
            for y in range(top + 3, top + 15):
                r = 6.2 - (y - top - 3) * 0.49
                for x in range(cx - 6, cx + 7):
                    for z in range(cz - 6, cz + 7):
                        distance = math.hypot(x - cx, z - cz)
                        if distance <= r:
                            if distance >= r - 1.3 or y == top + 3:
                                self.set(x, y, z, ROOF_EDGE if x == cx or z == cz else ROOF)
            self.set(cx, top + 15, cz, GOLD)
            self.set(cx, top + 16, cz, IRON)

    def roof(self):
        # Tall octagonal roof: dark timber fields, darker wooden ribs and a
        # layered cornice, followed by a glazed lantern and a pointed crown.
        for y in range(38, 66):
            half = 25 - (y - 38) * 0.7
            diagonal = half * 1.42
            for x in range(5, 59):
                for z in range(5, 59):
                    if not octagon(x, z, half, diagonal):
                        continue
                    if octagon(x, z, half - 1.8, diagonal - 2.5):
                        continue
                    dx, dz = abs(x - 31.5), abs(z - 31.5)
                    rib = abs(dx - dz) < 1 or dx < 1 or dz < 1
                    state = ROOF_EDGE if rib and y > 39 else ROOF
                    if y == 38:
                        state = QUARTZ
                    elif y == 39:
                        state = ROOF_EDGE
                    self.set(x, y, z, state)
        for y in range(66, 71):
            for x in range(26, 38):
                for z in range(26, 38):
                    r = math.hypot(x - 31.5, z - 31.5)
                    if r <= 5.5:
                        if y in (66, 70):
                            self.set(x, y, z, CHISEL if r > 4.1 else QUARTZ)
                        elif r > 4.1:
                            self.set(x, y, z, PILLAR if (x + z) % 3 == 0 else LIGHT_GLASS)
        for y in range(71, 80):
            r = max(0.85, 5.5 - (y - 71) * 0.6)
            for x in range(26, 38):
                for z in range(26, 38):
                    if math.hypot(x - 31.5, z - 31.5) <= r:
                        rib = x in (31, 32) or z in (31, 32)
                        self.set(x, y, z, GOLD if y == 79 else ROOF_EDGE if rib else ROOF)
        self.box(31, 80, 31, 31, 86, 31, ROOF_EDGE)
        self.set(31, 87, 31, GOLD)
        # A large, slightly waving red-and-gold royal standard is readable from
        # outside the compound. Its hoist touches the pole along its full height.
        for x in range(32, 41):
            wave = 0 if x <= 34 else 1 if x <= 37 else 2
            for y in range(82, 87):
                if x == 40 and y == 84:
                    continue
                self.set(x, y, 31 + wave, YELLOW if y == 84 or x == 35 else RED)
                if x in (35, 38):
                    self.set(x, y, 30 + wave, YELLOW if y == 84 or x == 35 else RED)

    def entrance(self):
        # Pointed, permanently open portal; no doors or portcullis.
        for x in range(24, 40):
            dx = abs(x - 31.5)
            height = 14 - math.ceil(max(0, dx - 2) * 0.9)
            for z in range(6, 11):
                for y in range(5, 5 + height):
                    state = QUARTZ if dx > 5 or y >= 3 + height else AIR
                    self.set(x, y, z, state)
        self.box(27, 5, 8, 36, 8, 17, AIR)
        # Heraldic crest above the portal.
        for x in range(28, 36):
            for y in range(29, 36):
                if abs(x - 31.5) <= (y - 27) / 2:
                    self.set(x, y, 7, RED)
                    if x in (31, 32) or y == 33:
                        self.set(x, y, 6, QUARTZ)
        for x in (24, 39):
            self.box(x, 5, 6, x, 20, 6, PILLAR)
            self.set(x, 21, 6, CHISEL)
        # The unobstructed ground-floor axis now ends at the plaza center.
        self.box(30, 5, 9, 33, 5, 34, CARPET)
        for x in (29, 34):
            self.box(x, 5, 9, x, 5, 34, GOLD_CARPET)

    def open_second_floor(self):
        # Remove the wall infill while retaining piers, arch shoulders and a
        # single outer railing. Tower shells are separate structural volumes.
        wall = set()
        for x in range(7, 57):
            for z in range(7, 57):
                if octagon(x, z) and not octagon(x, z, 22, 32):
                    wall.add((x, z))
        for side in range(4):
            for u in range(20, 44):
                for depth in range(7, 11):
                    wall.add(self.side(side, u, depth))
        for x, z in wall:
            if any(math.hypot(x - cx, z - cz) <= 5.5 for cx, cz in TOWERS):
                continue
            dx, dz = abs(x - 31.5), abs(z - 31.5)
            tangent = min(dx, dz)
            pier = tangent < 1 or abs(tangent - 8.5) < 0.6 or abs(dx - dz) < 1
            if pier:
                continue
            self.box(x, 17, z, x, 23, z, AIR)
            outside_edge = not octagon(x, z, 23, 33)
            self.set(x, 16, z, FENCE if outside_edge and octagon(x, z) else AIR)
            if self.get(x, 24, z) != AIR:
                self.set(x, 24, z, QUARTZ)
                if min(tangent, abs(tangent - 8.5)) <= 1.6:
                    self.set(x, 23, z, QUARTZ)

    def tower_entrances(self):
        # Independent towers are joined by a ground-level arcade and a railed
        # first gallery bridge. Open sides preserve the visible courtyard gaps.
        for cx, cz in TOWERS:
            sx, sz = (1 if cx < 32 else -1), (1 if cz < 32 else -1)
            path = set()
            for step in range(2, 14):
                x, z = cx + sx * step, cz + sz * step
                path.update((x + dx, z + dz) for dx in (-1, 0, 1) for dz in (-1, 0, 1))
            edge = {(x + dx, z + dz) for x, z in path
                    for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))} - path
            for floor in (4, 15):
                for x, z in path:
                    self.set(x, floor - 1, z, BRICK)
                    self.set(x, floor, z, ANDESITE)
                    self.box(x, floor + 1, z, x, floor + 4, z, AIR)
                for x, z in edge:
                    if not octagon(x, z) and math.hypot(x - cx, z - cz) > 5.5:
                        self.set(x, floor, z, QUARTZ)
                        if floor == 15:
                            self.set(x, floor + 1, z, FENCE)
                for step in (4, 11):
                    for cross in range(-2, 3):
                        x, z = cx + sx * (step + cross), cz + sz * (step - cross)
                        if abs(cross) == 2:
                            self.box(x, floor + 1, z, x, floor + 4, z, PILLAR)
                        self.set(x, floor + 5, z, GLOW if cross == 0 else QUARTZ)
            for x, z in path | edge:
                if not octagon(x, z) and math.hypot(x - cx, z - cz) > 5.5:
                    self.set(x, 20, z, TIMBER)
                    self.set(x, 21, z, ROOF)
            for step in (7, 10):
                for cross in (-2, 2):
                    x, z = cx + sx * (step + cross), cz + sz * (step - cross)
                    self.box(x, 5, z, x, 20, z, PILLAR)
                    self.set(x, 21, z, CHISEL)
                for floor in (4, 15):
                    x, z = cx + sx * step, cz + sz * step
                    self.set(x, floor, z, GLOW)

    def staircases(self):
        # Two-block flights leave an uninterrupted outer lane on each gallery.
        for first, second in ((range(11, 13), range(14, 16)),
                              (range(51, 53), range(48, 50))):
            for i in range(1, 12):
                z, y = 19 + i, 4 + i
                for x in first:
                    if i > 1:
                        if y > 6:
                            self.box(x, 5, z, x, y - 2, z, AIR)
                        self.set(x, y - 1, z, STEP_SUPPORT)
                    self.set(x, y, z, STAIRS["south"])
                    self.box(x, y + 1, z, x, y + 3, z, AIR)
                z, y = 31 - i, 15 + i
                for x in second:
                    if i > 1:
                        if y > 17:
                            self.box(x, 16, z, x, y - 2, z, AIR)
                        self.set(x, y - 1, z, STEP_SUPPORT)
                    self.set(x, y, z, STAIRS["north"])
                    self.box(x, y + 1, z, x, y + 3, z, AIR)
            xs = list(first) + list(second)
            self.box(min(xs), 15, 31, max(xs), 15, 34, ANDESITE)
            self.box(min(xs), 16, 31, max(xs), 19, 34, AIR)
            self.box(min(second), 26, 18, max(second), 26, 19, ANDESITE)
            self.box(min(second), 27, 18, max(second), 29, 19, AIR)

    def throne(self):
        # A compact royal balcony projects from the second-floor south gallery.
        # Its rear and both sides remain connected to the circular walkway.
        self.box(28, 14, 43, 35, 14, 50, CHISEL)
        self.box(28, 15, 43, 35, 15, 50, QUARTZ)
        for x in range(28, 36):
            for z in range(43, 51):
                if self.get(x, 16, z) == FENCE:
                    self.set(x, 16, z, AIR)
        self.box(28, 16, 43, 35, 16, 43, FENCE)
        for x in (28, 35):
            self.box(x, 16, 44, x, 16, 46, FENCE)
        self.box(29, 16, 45, 34, 16, 49, QUARTZ)
        self.box(30, 17, 45, 33, 17, 46, CARPET)
        self.box(31, 17, 47, 32, 17, 47, THRONE)
        self.box(31, 17, 48, 32, 19, 48, RED)
        for x in (30, 33):
            self.box(x, 17, 48, x, 19, 48, GOLD)
            self.set(x, 17, 47, QUARTZ_SLAB)
        self.box(30, 20, 48, 33, 20, 48, GOLD)

    def banner(self, x, y, z, facing):
        self.set(x, y, z, BANNERS[facing])
        self.banner_positions[(x + OFFSET_X, y, z + OFFSET_Z)] = facing

    def flags(self):
        # Large wool standards flank the upper-level windows; real patterned
        # banner block entities sit below the standards, facing the atrium.
        facing = ("south", "west", "north", "east")
        for side in range(4):
            for u in (22, 41):
                for du in range(-1, 2):
                    for y in range(29, 36):
                        if y == 29 and du == 0:
                            continue
                        x, z = self.side(side, u + du, 10)
                        self.set(x, y, z, YELLOW if du == 0 or y == 33 else RED)
                for du in range(-2, 3):
                    x, z = self.side(side, u + du, 10)
                    self.set(x, 36, z, TIMBER)
                x, z = self.side(side, u, 11)
                self.banner(x, 30, z, facing[side])
            for u in (30, 33):
                x, z = self.side(side, u, 10)
                self.banner(x, 33, z, facing[side])
        # Two short standards flank the second-floor throne.
        for x in (29, 34):
            self.box(x, 17, 49, x, 20, 49, LOG)
            self.set(x, 21, 49, GOLD)
            self.banner(x, 20, 48, "north")

    def lighting(self):
        for radius, count in ((0.8, 4), (7.5, 12), (14.5, 24)):
            for i in range(count):
                a = i * math.tau / count
                x, z = round(31.5 + radius * math.cos(a)), round(31.5 + radius * math.sin(a))
                self.set(x, 4, z, GLOW)
        for floor in (4, 15, 26):
            for i in range(32):
                a = i * math.tau / 32
                x, z = round(31.5 + 20.5 * math.cos(a)), round(31.5 + 20.5 * math.sin(a))
                if (self.get(x, floor + 1, z) == AIR
                        and self.get(x, floor, z) in (BRICK, QUARTZ, ANDESITE, TIMBER)):
                    self.set(x, floor, z, GLOW)
            for cx, cz in TOWERS:
                for dx, dz in ((-2, -2), (-2, 2), (2, -2), (2, 2)):
                    if self.get(cx + dx, floor, cz + dz) != AIR:
                        self.set(cx + dx, floor, cz + dz, GLOW)
        # Exposed lights on the column capitals brighten each occupied level.
        for index in range(12):
            a = math.radians(15 + index * 30)
            x, z = round(31.5 + 19 * math.cos(a)), round(31.5 + 19 * math.sin(a))
            for y in (13, 24, 35):
                for dx, dz in ((-1, -1), (-1, 1), (1, -1), (1, 1)):
                    if self.get(x + dx, y, z + dz) == CHISEL:
                        self.set(x + dx, y, z + dz, GLOW)
        # Smaller pendant lights hang beneath the two gallery decks.
        for floor in (4, 15):
            for index in range(12):
                a = index * math.tau / 12
                x, z = round(31.5 + 19 * math.cos(a)), round(31.5 + 19 * math.sin(a))
                if (self.get(x, floor + 10, z) != AIR
                        and all(self.get(x, y, z) == AIR for y in range(floor + 7, floor + 10))):
                    self.set(x, floor + 7, z, GLOW)
                    self.box(x, floor + 8, z, x, floor + 9, z, IRON)
        for x in (29, 34):
            self.set(x, 16, 45, GLOW)
        # An open chandelier ring is suspended from the lantern.
        for x in range(23, 41):
            for z in range(23, 41):
                r = math.hypot(x - 31.5, z - 31.5)
                if 7.2 <= r <= 8.2:
                    self.set(x, 33, z, ROOF_EDGE)
                    if (x + z) % 3 == 0:
                        self.set(x, 32, z, GLOW)
        for x, z in ((24, 31), (39, 32), (31, 24), (32, 39)):
            self.box(x, 34, z, x, 66, z, IRON)
            # The chain's roof anchor is a visible beam, not a floating block.
            if x in (24, 39):
                self.box(min(x, 31), 66, z, max(x, 32), 66, z, TIMBER)
            else:
                self.box(x, 66, min(z, 31), x, 66, max(z, 32), TIMBER)

    def annexes(self):
        for cx, purpose in ANNEXES:
            for x in range(cx - 15, cx + 16):
                for z in range(16, 48):
                    radius = math.hypot(x - cx, z - 31.5)
                    if radius > 15.5:
                        continue
                    self.set(x, 4, z, QUARTZ if (x + z) % 6 == 0 else ANDESITE)
                    if radius >= 13.5:
                        angle = math.atan2(z - 31.5, x - cx)
                        bay = (angle + math.pi / 12) % (math.pi / 6) - math.pi / 12
                        tangent = abs(bay * 14)
                        for y in range(5, 26):
                            state = self.stone(x, y, z)
                            for base in (7, 18):
                                top = base + 5 - math.floor(tangent)
                                if tangent < 2.3 and base <= y <= top:
                                    state = QUARTZ if tangent > 1.5 or y == top else LIGHT_GLASS if y % 3 else BLUE_GLASS
                            self.set(x, y, z, state)
                        for y in (5, 14, 25):
                            self.set(x, y, z, QUARTZ if y == 14 else CHISEL)
                    elif radius >= 7.5:
                        self.set(x, 14, z, BRICK)
                        self.set(x, 15, z, TIMBER)
                        if any(math.hypot(x + dx - cx, z + dz - 31.5) < 7.5
                               for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1))):
                            self.set(x, 16, z, FENCE)
            for index in range(12):
                angle = (index + 0.5) * math.tau / 12
                x, z = round(cx + 15 * math.cos(angle)), round(31.5 + 15 * math.sin(angle))
                self.box(x, 5, z, x, 25, z, PILLAR)
                self.set(x, 26, z, CHISEL)
            # A circular eave and twelve dark ribs form a pointed conical roof.
            for y in range(26, 45):
                radius = 16.5 - (y - 26) * 0.85
                for x in range(cx - 16, cx + 17):
                    for z in range(15, 49):
                        r = math.hypot(x - cx, z - 31.5)
                        if not max(0, radius - 1.7) <= r <= radius:
                            continue
                        a = math.atan2(z - 31.5, x - cx)
                        rib = abs(math.sin(6 * a)) * r < 1.5
                        self.set(x, y, z, QUARTZ if y == 26 else ROOF_EDGE if rib or y == 27 else ROOF)
            for z in (31, 32):
                self.set(cx, 45, z, CHISEL)
                self.set(cx, 46, z, IRON)
            # The former exterior portal is now uninterrupted circular masonry.
            for dx in (-4, 4):
                self.banner(cx + dx, 21, 16, "north")
            for x in (cx - 6, cx + 6):
                for z in (23, 40):
                    self.box(x, 5, z, x, 24, z, PILLAR)
                    for y in (13, 24):
                        self.set(x, y, z, GLOW)
            self.annex_furniture(cx, purpose)
            for i in range(16):
                a = i * math.tau / 16
                x, z = round(cx + 10.5 * math.cos(a)), round(31.5 + 10.5 * math.sin(a))
                for floor in (4, 15):
                    if self.get(x, floor + 1, z) == AIR:
                        self.set(x, floor, z, GLOW)
            self.box(cx, 23, 31, cx, 44, 31, IRON)
            self.box(cx - 3, 22, 31, cx + 3, 22, 31, ROOF_EDGE)
            self.box(cx, 22, 28, cx, 22, 34, ROOF_EDGE)
            for dx, dz in ((-3, 0), (3, 0), (0, -3), (0, 3)):
                self.set(cx + dx, 21, 31 + dz, GLOW)
            sign = -1 if cx < 32 else 1
            x1, x2 = sorted((cx + sign * 8, cx + sign * 10))
            for z in range(23, 34):
                y = z - 18
                for x in range(x1, x2 + 1):
                    if y > 6:
                        self.box(x, 5, z, x, y - 2, z, AIR)
                    if y > 5:
                        self.set(x, y - 1, z, STEP_SUPPORT)
                    self.set(x, y, z, STAIRS["south"])
                    self.box(x, y + 1, z, x, y + 3, z, AIR)
            self.box(x1, 15, 34, x2, 15, 36, ANDESITE)
            self.box(x1, 16, 34, x2, 19, 36, AIR)

    def annex_furniture(self, cx, purpose):
        if purpose == "banquet":
            for x in (cx - 3, cx + 3):
                for z in range(26, 39):
                    if z in (26, 30, 34, 38):
                        self.set(x, 5, z, FENCE)
                    self.set(x, 6, z, TABLE_TOP)
                for z in (27, 30, 35, 38):
                    self.set(x - 2, 5, z, SEATS["west"])
                    self.set(x + 2, 5, z, SEATS["east"])
            self.box(cx - 1, 5, 20, cx + 1, 5, 42, CARPET)
            for x in range(cx - 4, cx + 5):
                self.set(x, 16, 42, SEATS["south"])
        else:
            for floor in (4, 15):
                for x in range(cx - 12, cx + 13):
                    for z in range(19, 45):
                        r = math.hypot(x - cx, z - 31.5)
                        if not 11.7 <= r < 12.8:
                            continue
                        if (x > cx + 6 and 21 <= z <= 37) or (x < cx and 28 <= z <= 35):
                            continue
                        self.box(x, floor + 1, z, x, floor + 3, z, BOOKSHELF)
            for z in (25, 37):
                for x in range(cx - 3, cx + 4):
                    if x in (cx - 3, cx + 3):
                        self.set(x, 5, z, FENCE)
                    self.set(x, 6, z, TABLE_TOP)
                for x in (cx - 2, cx, cx + 2):
                    self.set(x, 5, z - 2, SEATS["north"])
                    self.set(x, 5, z + 2, SEATS["south"])
            for x in (cx - 4, cx, cx + 4):
                self.set(x, 16, 21, SEATS["north"])

    def annex_connections(self):
        for x1, x2, gap1, gap2 in ((-9, 10, -2, 7), (53, 72, 56, 65)):
            for floor in (4, 15):
                self.box(x1, floor - 1, 30, x2, floor, 33, ANDESITE)
                self.box(x1, floor + 1, 30, x2, floor + 4, 33, AIR)
                for z in (29, 34):
                    self.box(gap1, floor, z, gap2, floor, z, QUARTZ)
                    if floor == 15:
                        self.box(gap1, floor + 1, z, gap2, floor + 1, z, FENCE)
                for x in (gap1 + 2, gap2 - 2):
                    for z in (29, 34):
                        self.box(x, floor + 1, z, x, floor + 5, z, PILLAR)
                    self.box(x, floor + 5, 30, x, floor + 5, 33, QUARTZ)
                    self.set(x, floor + 5, 31, GLOW)
            self.box(gap1, 21, 29, gap2, 21, 34, ROOF)
            self.box(gap1, 22, 30, gap2, 22, 33, ROOF)

    def raise_palace(self):
        # One low, rounded esplanade carries the complete palace group. The
        # main hall has a second plinth, joined by one-block transition bands.
        for z in range(-5, 69):
            for x in range(-39, 103):
                dx, dz = max(abs(x - 31.5) - 25.5, 0), max(abs(z - 31.5) - 25.5, 0)
                on_terrace = math.hypot(dx, dz) <= 10
                on_terrace |= any(math.hypot(x - cx, z - 31.5) <= 19.5 for cx, _ in ANNEXES)
                if not on_terrace:
                    continue
                lift = MAIN_LIFT if octagon(x, z, 26, 37) else 4 if octagon(x, z, 27, 39) else TERRACE_LIFT
                zi, xi = z + OFFSET_Z, x + OFFSET_X
                column = self.blocks[:, zi, xi].copy()
                assert np.all(column[HEIGHT - lift:] == AIR)
                self.blocks[4 + lift:, zi, xi] = column[4:HEIGHT - lift]
                self.blocks[4:4 + lift, zi, xi] = BRICK
                self.set(x, 3 + lift, z, CHISEL)
                self.ground[zi, xi] = 4 + lift
                self.palace_lift[zi, xi] = lift
        self.banner_positions = {(x, y + int(self.palace_lift[z, x]), z): face
                                 for (x, y, z), face in self.banner_positions.items()}
        # Transition treads on both levels of each connecting gallery.
        connection_cells = set()
        for x1, x2 in ((-9, 10), (53, 72)):
            connection_cells.update((x, z) for x in range(x1, x2 + 1) for z in range(30, 34))
        for cx, cz in TOWERS:
            sx, sz = (1 if cx < 32 else -1), (1 if cz < 32 else -1)
            for step in range(2, 14):
                connection_cells.update((cx + sx * step + dx, cz + sz * step + dz)
                                        for dx in (-1, 0, 1) for dz in (-1, 0, 1))
        for x, z in connection_cells:
            lift = int(self.palace_lift[z + OFFSET_Z, x + OFFSET_X])
            for face, dx, dz in (("east", -1, 0), ("west", 1, 0), ("south", 0, -1), ("north", 0, 1)):
                if (x + dx, z + dz) not in connection_cells:
                    continue
                other = int(self.palace_lift[z + dz + OFFSET_Z, x + dx + OFFSET_X])
                if lift == other + 1:
                    for floor in (4, 15):
                        if self.get(x, floor + lift + 1, z) == AIR:
                            self.set(x, floor + lift, z, STAIRS[face])
                    break
        # Wide entrance and garden stairs connect the esplanade to the court.
        for z, y in ((-7, 5), (-6, 6), (-5, 7), (5, 8), (6, 9)):
            self.box(27, 4, z, 36, y - 1, z, BRICK)
            self.box(27, y, z, 36, y, z, STAIRS["south"])
            self.box(27, y + 1, z, 36, y + 4, z, AIR)
            self.ground[z + OFFSET_Z, 27 + OFFSET_X:37 + OFFSET_X] = y
        for cx, end in ((31, 67), (-18, 51), (81, 51)):
            self.box(cx - 3, 4, end - 2, cx + 3, 6, end, BRICK)
            self.box(cx - 3, 7, end - 2, cx + 3, 7, end, ANDESITE)
            self.ground[end - 2 + OFFSET_Z:end + 1 + OFFSET_Z, cx - 3 + OFFSET_X:cx + 4 + OFFSET_X] = 7
            for step in range(1, 4):
                y, z = 8 - step, end + step
                self.box(cx - 3, 4, z, cx + 3, y - 1, z, BRICK)
                self.box(cx - 3, y, z, cx + 3, y, z, STAIRS["north"])
                self.ground[z + OFFSET_Z, cx - 3 + OFFSET_X:cx + 4 + OFFSET_X] = y

    def timber_building(self, x1, x2, z1, z2):
        self.box(x1, 4, z1, x2, 4, z2, TIMBER)
        for x in range(x1, x2 + 1):
            for z in range(z1, z2 + 1):
                if x not in (x1, x2) and z not in (z1, z2):
                    continue
                for y in range(5, 13):
                    post = x in (x1, x2) and (z - z1) % 5 == 0 or z in (z1, z2) and (x - x1) % 5 == 0
                    state = BRICK if y <= 6 else LOG if post or y == 12 else TIMBER
                    if x in (x1, x2) and (z - z1) % 5 in (2, 3) and 8 <= y <= 10:
                        state = LIGHT_GLASS
                    self.set(x, y, z, state)
        center = (x1 + x2) / 2
        for y in range(13, 23):
            half = (x2 - x1) / 2 + 1 - (y - 13) * 1.25
            for x in range(x1 - 1, x2 + 2):
                if abs(x - center) > half:
                    continue
                for z in range(z1 - 1, z2 + 2):
                    if abs(x - center) >= half - 1.5:
                        self.set(x, y, z, ROOF_EDGE if z in (z1 - 1, z2 + 1) else ROOF)
                    elif z in (z1, z2):
                        self.set(x, y, z, TIMBER)
        for x in (x1 + 2, x2 - 2):
            for z in (z1 + 3, z2 - 3):
                self.set(x, 4, z, GLOW)
        self.box(round(center), 13, z1, round(center), 13, z2, LOG)
        self.set(round(center), 12, (z1 + z2) // 2, GLOW)

    def service_buildings(self):
        self.timber_building(-30, -8, 62, 78)
        self.box(-21, 5, 62, -17, 9, 62, AIR)
        for x in (-26, -22, -18, -14):
            for z in (68, 74):
                self.set(x, 5, z, CHEST)
                self.chest_positions.add((x + OFFSET_X, 5, z + OFFSET_Z))
        self.box(-29, 5, 74, -28, 7, 77, LOG)
        self.box(-11, 5, 74, -9, 6, 77, HAY)
        self.timber_building(70, 94, 62, 80)
        self.box(78, 5, 62, 85, 10, 62, AIR)
        for x in (71, 79, 87, 93):
            self.box(x, 5, 73, x, 5, 79, WOOD_FENCE)
        self.box(71, 5, 73, 93, 5, 73, WOOD_FENCE)
        for x in (75, 83, 91):
            self.set(x, 5, 73, STALL_GATE)
            self.box(x - 1, 5, 77, x + 1, 5, 79, QUARTZ)
            self.set(x, 5, 78, WATER)
            self.set(x - 2, 5, 77, HAY)
            self.set(x - 2, 6, 77, HAY)
        self.box(72, 5, 65, 74, 6, 68, HAY)

    def training_yard(self):
        for x in range(16, 48):
            for z in range(78, 100):
                edge = x in (16, 47) or z in (78, 99)
                self.set(x, 4, z, BRICK if edge else SAND if (x * 17 + z * 11) % 13 else COARSE_DIRT)
                if edge:
                    self.set(x, 5, z, WOOD_FENCE)
        self.box(28, 5, 78, 35, 7, 78, AIR)
        for cx in (23, 40):
            self.box(cx, 5, 97, cx, 9, 97, LOG)
            for x in range(cx - 2, cx + 3):
                for y in range(6, 11):
                    d = max(abs(x - cx), abs(y - 8))
                    self.set(x, y, 96, RED if d in (0, 2) else WHITE)
        for x in (22, 41):
            self.box(x, 5, 86, x, 7, 86, WOOD_FENCE)
            self.box(x - 1, 7, 86, x + 1, 7, 86, TIMBER)
            self.set(x, 8, 86, HAY)
        for z in (83, 87, 91):
            self.set(17, 5, z, SEATS["west"])
        self.box(44, 5, 80, 46, 5, 82, LOG)

    def fountain(self):
        cx, cz = FOUNTAIN
        for x in range(16, 48):
            for z in range(cz - 15, cz + 16):
                r = math.hypot(x - cx, z - cz)
                if r > 15:
                    continue
                self.set(x, 4, z, QUARTZ if 13.5 < r < 14.5 else ANDESITE)
                self.box(x, 5, z, x, 8, z, AIR)
                if 8.5 < r <= 10:
                    self.set(x, 5, z, QUARTZ)
                    self.set(x, 6, z, QUARTZ_SLAB)
                elif r <= 8.5:
                    self.set(x, 4, z, GLOW if (x + z) % 5 == 0 else QUARTZ)
                    self.set(x, 5, z, WATER)
                if r <= 2.6:
                    self.box(x, 5, z, x, 11, z, CHISEL)
                elif r <= 4.5:
                    self.set(x, 7, z, QUARTZ)
                    self.set(x, 8, z, QUARTZ if r > 3.6 else WATER)
        for x, z in ((27, cz), (36, cz), (31, cz - 4), (32, cz + 4)):
            self.set(x, 8, z, WATER)
            self.box(x, 6, z, x, 7, z, FALLING_WATER)
        # A broad heraldic silhouette replaces the detailed head and talons.
        # Image coordinates run downwards; the sculpture rises from Y=12 to 33.
        silhouette = Image.new("1", (36, 22))
        pen = ImageDraw.Draw(silhouette)
        wing = [(0, 0), (5, 3), (11, 7), (17, 9), (17, 15),
                (10, 16), (7, 14), (11, 12), (5, 13), (3, 10),
                (8, 10), (2, 7)]
        pen.polygon(wing, fill=1)
        pen.polygon([(35 - x, y) for x, y in wing], fill=1)
        pen.polygon([(16, 4), (19, 4), (21, 10), (20, 16), (24, 20),
                     (21, 21), (18, 18), (17, 21), (14, 21), (11, 20),
                     (15, 16), (14, 10)], fill=1)
        for row, column in np.argwhere(np.asarray(silhouette)):
            x, y = 14 + int(column), 33 - int(row)
            self.box(x, y, cz - 1, x, y, cz + 1, QUARTZ)
            if 14 <= column <= 21:
                self.set(x, y, cz + 2, ANDESITE)
        self.box(30, 11, cz, 33, 14, cz + 1, QUARTZ)
        # The supplied transparent artwork becomes a larger freestanding crest
        # above the eagle, retaining its negative spaces without a shield panel.
        with Image.open(OUT / "royal_crest_reference.png") as reference:
            mask = reference.convert("RGBA").getchannel("A").resize((36, 22), Image.Resampling.LANCZOS)
        self.crest_mask = np.asarray(mask) >= 144
        for row, column in np.argwhere(self.crest_mask):
            self.box(14 + int(column), 57 - int(row), cz - 1,
                     14 + int(column), 57 - int(row), cz, YELLOW)
        self.box(31, 30, cz + 1, 32, 37, cz + 1, QUARTZ)

    def courtyard_roads(self):
        # A continuous promenade sits inside the curtain wall, with short
        # approaches to the bastions and the two gate-side wall staircases.
        path = set()
        for z in range(Z_MIN, Z_MAX + 1):
            for x in range(X_MIN, X_MAX + 1):
                d = self.wall_distance[z + OFFSET_Z, x + OFFSET_X]
                if -10.5 <= d <= -7:
                    path.add((x, z))
        for cx, cz in FORTS:
            direction = 1 if cx < 32 else -1
            path.update((cx + direction * step, cz + dz) for step in range(3, 15) for dz in (-1, 0, 1))
        path.update((x, z) for x in range(19, 45) for z in range(GATE_Z + 16, GATE_Z + 19))
        path.update((x, z) for x in range(27, 37) for z in range(GATE_Z + 7, 9)
                    if math.hypot(x - FOUNTAIN[0], z - FOUNTAIN[1]) > 10.5)
        path.update((x, z) for x in range(16, 48) for z in range(-42, -11)
                    if 10.5 < math.hypot(x - FOUNTAIN[0], z - FOUNTAIN[1]) <= 15)
        for cx in (-18, 81):
            path.update((x, z) for x in range(cx - 3, cx + 4) for z in range(55, 62))
        path.update((x, z) for x in range(28, 36) for z in range(71, 78))
        path.update((x, z) for x in range(-5, 69) for z in range(72, 75))
        for x1, x2 in ((-21, -4), (67, 85)):
            path.update((x, z) for x in range(x1, x2 + 1) for z in range(59, 62))
        for x1 in (-6, 67):
            path.update((x, z) for x in range(x1, x1 + 3) for z in range(60, 75))
        for x1 in (1, 58):
            path.update((x, z) for x in range(x1, x1 + 5) for z in range(74, 104))
        for x, z in path:
            h = int(self.ground[z + OFFSET_Z, x + OFFSET_X])
            if (self.get(x, h, z) in (GRASS, BRICK, QUARTZ, ANDESITE, GLOW)
                    and all(self.get(x, y, z) == AIR for y in range(h + 1, h + 4))):
                self.set(x, h, z, GLOW if (x * 7 + z * 3) % 83 == 0 else ANDESITE)
                self.courtyard_paths.add((x, z))

    def rolling_terrain(self):
        # Smooth seeded noise is tapered away from buildings and paths; the
        # resulting small hills and hollows remain independent of the roads.
        rng = np.random.default_rng(12012026)
        coarse = rng.uniform(-1, 1, (18, 18)).astype(np.float32)
        smooth = np.asarray(Image.fromarray(coarse).resize((WIDTH, LENGTH), Image.Resampling.BICUBIC))
        desired = np.clip(np.rint(smooth * 2.6 + 0.4), -1, 3).astype(np.int16)
        eligible = ((self.blocks[4] == GRASS) & np.all(self.blocks[5:27] == AIR, axis=0)
                    & (self.wall_distance < -12))
        core = eligible.copy()
        delta = np.zeros_like(self.ground)
        for depth in (1, 2, 3):
            core &= np.roll(core, 1, 0) & np.roll(core, -1, 0) & np.roll(core, 1, 1) & np.roll(core, -1, 1)
            delta[core] = np.clip(desired[core], -1, depth)
        for zi, xi in np.argwhere(delta != 0):
            x, z, h = int(xi - OFFSET_X), int(zi - OFFSET_Z), 4 + int(delta[zi, xi])
            self.box(x, 3, z, x, 7, z, AIR)
            if h > 3:
                self.box(x, 3, z, x, h - 1, z, DIRT)
            self.set(x, h, z, GRASS)
            self.ground[zi, xi] = h
            self.terrain_cells.add((x, z))

    def gardens(self):
        candidates = [(-5, 2), (68, 2), (-10, 11), (73, 11),
                      (-10, 54), (73, 54), (-5, 63), (68, 63),
                      (16, 67), (47, 67), (31, 70),
                      (0, 22), (63, 22), (0, 42), (63, 42),
                      (-12, -22), (75, -22), (8, -31), (55, -31),
                      (8, -12), (55, -12), (-34, -10), (97, -10),
                      (-43, 17), (106, 17), (-43, 45), (106, 45),
                      (-14, 87), (78, 88), (10, 91), (53, 91)]
        for cx, cz in candidates:
            if self.wall_distance[cz + OFFSET_Z, cx + OFFSET_X] > -11:
                continue
            if any(math.hypot(cx - x, cz - z) < 9 for x, z in self.tree_positions):
                continue
            base = int(self.ground[cz + OFFSET_Z, cx + OFFSET_X])
            if not all(self.get(x, int(self.ground[z + OFFSET_Z, x + OFFSET_X]), z) == GRASS
                       and all(self.get(x, y, z) == AIR for y in range(max(base + 1, int(self.ground[z + OFFSET_Z, x + OFFSET_X]) + 1), base + 13))
                       for x in range(cx - 3, cx + 4) for z in range(cz - 3, cz + 4)):
                continue
            birch = len(self.tree_positions) % 3 == 1
            log, leaves = (BIRCH_LOG, BIRCH_LEAVES) if birch else (OAK_LOG, OAK_LEAVES)
            top = base + (8 if birch else 7)
            for x in range(cx - 3, cx + 4):
                for z in range(cz - 3, cz + 4):
                    for y in range(top - 4, top + 3):
                        if ((x - cx) / 3.4) ** 2 + ((z - cz) / 3.4) ** 2 + ((y - top + 1) / 3.2) ** 2 <= 1:
                            self.set(x, y, z, leaves)
            self.box(cx, base + 1, cz, cx, top, cz, log)
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                self.set(cx + dx, top - 2, cz + dz, log)
            self.tree_positions.append((cx, cz))
        for cz in (-34, -14, 3, 13, 50, 60, 88):
            for cx in (-39, -18, -2, 15, 48, 65, 81, 102):
                if self.wall_distance[cz + OFFSET_Z, cx + OFFSET_X] > -12:
                    continue
                if any(math.hypot(cx - x, cz - z) < 7 for x, z in self.tree_positions):
                    continue
                base = int(self.ground[cz + OFFSET_Z, cx + OFFSET_X])
                if not all(abs(int(self.ground[z + OFFSET_Z, x + OFFSET_X]) - base) <= 1
                           and self.get(x, int(self.ground[z + OFFSET_Z, x + OFFSET_X]), z) == GRASS
                           and all(self.get(x, y, z) == AIR for y in range(int(self.ground[z + OFFSET_Z, x + OFFSET_X]) + 1, base + 3))
                           for x in range(cx - 3, cx + 4) for z in range(cz - 3, cz + 4)):
                    continue
                for x in range(cx - 3, cx + 4):
                    for z in range(cz - 3, cz + 4):
                        r = math.hypot(x - cx, z - cz)
                        if r > 3.3:
                            continue
                        self.box(x, base - 1, z, x, base + 1, z, DIRT)
                        self.set(x, base + 1, z, AIR)
                        self.ground[z + OFFSET_Z, x + OFFSET_X] = base
                        if r > 2.4:
                            self.set(x, base + 1, z, SLAB)
                        else:
                            self.set(x, base + 1, z, FLOWERS[(x + z + cx) % len(FLOWERS)])
                self.garden_beds.append((cx, cz))
        # Sparse flowers and low grass soften the remaining lawn without
        # consuming the open court or obstructing any paved routes.
        for z in range(-40, 108):
            for x in range(-48, 112):
                if self.wall_distance[z + OFFSET_Z, x + OFFSET_X] > -11:
                    continue
                base = int(self.ground[z + OFFSET_Z, x + OFFSET_X])
                if self.get(x, base, z) != GRASS or self.get(x, base + 1, z) != AIR:
                    continue
                if any(self.get(x, y, z) != AIR for y in range(base + 2, base + 12)):
                    continue
                value = (x * 79 + z * 137 + x * z * 3) % 101
                if value < 4:
                    self.set(x, base + 1, z, FLOWERS[value % len(FLOWERS)])
                elif value < 9:
                    self.set(x, base + 1, z, FERN if value == 8 else SHORT_GRASS)

    def connect_rails(self):
        for y, z, x in np.argwhere(np.isin(self.blocks, [FENCE, IRON, WOOD_FENCE])):
            x, z = int(x) - OFFSET_X, int(z) - OFFSET_Z
            source = MATERIALS[self.get(x, y, z)]
            props = dict(source.properties)
            for face, dx, dz in (("east", 1, 0), ("west", -1, 0), ("south", 0, 1), ("north", 0, -1)):
                if not (X_MIN <= x + dx <= X_MAX and Z_MIN <= z + dz <= Z_MAX):
                    continue
                neighbor = MATERIALS[self.get(x + dx, y, z + dz)].name
                props[face] = str(neighbor not in ("air", "water", "red_carpet", "yellow_carpet")).lower()
            self.set(x, y, z, material(source.name, source.legacy_id, source.legacy_data, source.color, **props))

    @staticmethod
    def patterns():
        return List[Compound]([
            Compound({"Pattern": String("cs"), "Color": Int(4)}),
            Compound({"Pattern": String("ms"), "Color": Int(4)}),
            Compound({"Pattern": String("bo"), "Color": Int(4)}),
            Compound({"Pattern": String("mc"), "Color": Int(0)}),
        ])

    def block_entity_nbt(self, position, legacy=False):
        if position in self.chest_positions:
            result = Compound({"id": String("Chest" if legacy else "minecraft:chest"), "Items": List[Compound]()})
            if legacy:
                result.update({k: Int(v) for k, v in zip(("x", "y", "z"), position)})
            return result
        result = Compound({"id": String("Banner" if legacy else "minecraft:banner"),
                           "Patterns": self.patterns()})
        if legacy:
            # Both Base and pattern colors use reversed dye numbering in MCEdit.
            result["Base"] = Int(1)
            for pattern in result["Patterns"]:
                pattern["Color"] = Int(15 - int(pattern["Color"]))
            for k, v in zip(("x", "y", "z"), position):
                result[k] = Int(v)
        return result

    def export(self):
        OUT.mkdir(parents=True, exist_ok=True)
        blocks = List[Compound]()
        for y in range(HEIGHT):
            for z in range(LENGTH):
                for x in range(WIDTH):
                    entry = Compound({"pos": List[Int]([x, y, z]), "state": Int(self.blocks[y, z, x])})
                    if (x, y, z) in self.banner_positions or (x, y, z) in self.chest_positions:
                        entry["nbt"] = self.block_entity_nbt((x, y, z))
                    blocks.append(entry)
        structure = File({"DataVersion": Int(DATA_VERSION), "size": List[Int]([WIDTH, HEIGHT, LENGTH]),
                          "palette": List[Compound]([m.tag() for m in MATERIALS]),
                          "blocks": blocks, "entities": List[Compound]()})
        structure.save(OUT / (NAME + ".nbt"), gzipped=True)
        # Serialize each record through the library; one record per line keeps
        # the large, explicit-air template practical to inspect and edit.
        with (OUT / (NAME + ".snbt")).open("w", encoding="utf-8", newline="\n") as target:
            target.write('{DataVersion:' + str(DATA_VERSION) + ',size:' + structure["size"].snbt() + ',palette:')
            target.write(structure["palette"].snbt())
            target.write(',entities:[],blocks:[\n')
            for i, block in enumerate(blocks):
                target.write(block.snbt() + (",\n" if i + 1 < len(blocks) else "\n"))
            target.write("]}\n")
        ids = np.asarray([m.legacy_id for m in MATERIALS], dtype=np.uint8)[self.blocks]
        data = np.asarray([m.legacy_data for m in MATERIALS], dtype=np.uint8)[self.blocks]
        schematic = File({
            "Width": Short(WIDTH), "Height": Short(HEIGHT), "Length": Short(LENGTH),
            "Materials": String("Alpha"),
            "Blocks": ByteArray(ids.flatten().view(np.int8)),
            "Data": ByteArray(data.flatten().view(np.int8)),
            "Entities": List[Compound](),
            "TileEntities": List[Compound]([self.block_entity_nbt(p, legacy=True)
                                            for p in sorted(set(self.banner_positions) | self.chest_positions)]),
            "WEOriginX": Int(0), "WEOriginY": Int(0), "WEOriginZ": Int(0),
            "WEOffsetX": Int(-31 - OFFSET_X), "WEOffsetY": Int(-5), "WEOffsetZ": Int(0),
        }, root_name="Schematic")
        schematic.save(OUT / (NAME + ".schematic"), gzipped=True)
        with zipfile.ZipFile(OUT / "royal_main_hall_datapack.zip", "w", zipfile.ZIP_DEFLATED) as pack:
            pack.writestr("pack.mcmeta", json.dumps({"pack": {"pack_format": 15,
                          "description": f"Royal Palace | Minecraft 1.20.1 | {WIDTH} x {HEIGHT} x {LENGTH}"}}))
            pack.write(OUT / (NAME + ".nbt"), "data/royal_hall/structures/main_hall.nbt")
        return structure


def validate_exports(castle, structure, client_jar):
    print("Validating SNBT and binary NBT round trips...", flush=True)
    parsed = nbtlib.parse_nbt((OUT / (NAME + ".snbt")).read_text(encoding="utf-8"))
    assert parsed == Compound(structure)
    assert list(map(int, parsed["size"])) == [WIDTH, HEIGHT, LENGTH]
    assert len(parsed["blocks"]) == WIDTH * HEIGHT * LENGTH
    del parsed
    assert nbtlib.load(OUT / (NAME + ".nbt")) == structure
    schematic = nbtlib.load(OUT / (NAME + ".schematic"))
    assert schematic.root_name == "Schematic"
    assert tuple(int(schematic[k]) for k in ("Width", "Height", "Length")) == (WIDTH, HEIGHT, LENGTH)
    ids = np.asarray(schematic["Blocks"]).view(np.uint8)
    data = np.asarray(schematic["Data"]).view(np.uint8)
    expected_ids = np.array([m.legacy_id for m in MATERIALS], dtype=np.uint8)[castle.blocks].flatten()
    expected_data = np.array([m.legacy_data for m in MATERIALS], dtype=np.uint8)[castle.blocks].flatten()
    assert np.array_equal(ids, expected_ids) and np.array_equal(data, expected_data)
    assert len(castle.banner_positions) == 22 and len(castle.chest_positions) == 8
    assert len(schematic["TileEntities"]) == 30
    # Check all block names and specified state values against the actual 1.20.1 assets.
    with zipfile.ZipFile(client_jar) as jar:
        for mat in MATERIALS:
            path = "assets/minecraft/blockstates/" + mat.name + ".json"
            if mat.name in ("air", "water") or mat.name.endswith("wall_banner"):
                continue
            asset = json.loads(jar.read(path))
            if "variants" in asset:
                valid = {}
                for variant in asset["variants"]:
                    for prop in variant.split(","):
                        if "=" in prop:
                            k, v = prop.split("=", 1)
                            valid.setdefault(k, set()).add(v)
                for k, v in mat.properties:
                    if k in valid:
                        assert v in valid[k], (mat.name, k, v)


def validate_geometry(castle):
    m, s = MAIN_LIFT, TERRACE_LIFT
    # Verify banner orientation, attachment, and a clear two-block hanging face.
    for (x, y, z), face in castle.banner_positions.items():
        x, z = x - OFFSET_X, z - OFFSET_Z
        assert castle.get(x, y, z) == BANNERS[face]
        dx, dz = {"north": (0, 1), "south": (0, -1), "east": (-1, 0), "west": (1, 0)}[face]
        assert castle.get(x + dx, y, z + dz) not in (AIR, WATER)
        assert castle.get(x, y - 1, z) == AIR
    # Every flag, stair, lamp and moat source must fit the selection.
    assert int(np.argwhere(castle.blocks != AIR)[:, 0].max()) == 87 + m
    assert not any("door" in mat.name for mat in MATERIALS)
    for y in range(6, 9):
        assert all(castle.get(x, y + m, z) == AIR for x in range(27, 37) for z in range(8, 14))
    assert all(castle.get(31, y + m, 25) == AIR for y in range(6, 32))
    for y in (15, 26):
        assert all(castle.get(x, y + m, z) == AIR for x, z in ((31, 24), (24, 31), (39, 31), (31, 39)))
    assert np.all(castle.blocks[6 + m:14 + m, 27 + OFFSET_Z:39 + OFFSET_Z,
                               25 + OFFSET_X:39 + OFFSET_X] == AIR), "Ground-floor throne was not removed"
    assert set(map(tuple, np.argwhere(castle.blocks == THRONE))) == {
        (17 + m, 47 + OFFSET_Z, 31 + OFFSET_X), (17 + m, 47 + OFFSET_Z, 32 + OFFSET_X)}
    for side in range(4):
        for u in (26, 37):
            for depth in (8, 9):
                x, z = castle.side(side, u, depth)
                assert all(castle.get(x, y + m, z) == AIR for y in range(18, 23)), ("Blocked arcade", x, z)
    assert MATERIALS[ROOF].name == "dark_oak_planks"
    assert MATERIALS[ROOF_EDGE].name == "dark_oak_log"
    for first, second in ((range(11, 13), range(14, 16)), (range(51, 53), range(48, 50))):
        for step in range(1, 12):
            for xs, y, z, face in ((first, 4 + step + m, 19 + step, "south"),
                                    (second, 15 + step + m, 31 - step, "north")):
                for x in xs:
                    assert castle.get(x, y, z) == STAIRS[face], ("Obstructed stair tread", x, y, z)
                    assert castle.get(x, y + 1, z) == castle.get(x, y + 2, z) == AIR
                    bottom = (5 if face == "south" else 16) + m
                    assert all(castle.get(x, below, z) == AIR for below in range(bottom, y - 1)), ("Solid stair infill", x, y, z)
    annex_tops = []
    for cx, _ in ANNEXES:
        sign = -1 if cx < 32 else 1
        for z in range(23, 34):
            y = z - 18 + s
            for x in (cx + sign * step for step in (8, 9, 10)):
                assert castle.get(x, y, z) == STAIRS["south"]
                assert castle.get(x, y + 1, z) == castle.get(x, y + 2, z) == AIR
                assert all(castle.get(x, below, z) == AIR for below in range(5 + s, y - 1))
        window = castle.blocks[:, 15 + OFFSET_Z:49 + OFFSET_Z,
                               cx - 16 + OFFSET_X:cx + 17 + OFFSET_X]
        annex_tops.append(int(np.argwhere(window != AIR)[:, 0].max()))
        assert all(castle.get(cx, y + s, 31) == AIR for y in range(8, 21)), "Annex central void is blocked"
        for floor in (4 + s, 15 + s):
            assert np.count_nonzero(window[floor:floor + 11] == GLOW) >= 6
        for x in range(cx - 15, cx + 16):
            for z in range(16, 48):
                if 13.5 <= math.hypot(x - cx, z - 31.5) <= 15.5:
                    if 30 <= z <= 33 and (x - cx) * sign < 0:
                        continue
                    assert castle.get(x, 5 + s, z) != AIR and castle.get(x, 16 + s, z) != AIR, ("Exterior annex opening", x, z)
    assert annex_tops == [46 + s, 46 + s]
    for x1, x2 in ((-2, 7), (56, 65)):
        assert all(castle.get(x, y + int(castle.palace_lift[31 + OFFSET_Z, x + OFFSET_X]), 31) == AIR
                   for x in range(x1 + 2, x2 - 1) for y in range(23, 37)), "Annex gap is filled"
    tower_tops = []
    for cx, cz in TOWERS:
        window = castle.blocks[:, cz + OFFSET_Z - 6:cz + OFFSET_Z + 7,
                               cx + OFFSET_X - 6:cx + OFFSET_X + 7]
        tower_tops.append(int(np.argwhere(window != AIR)[:, 0].max()))
        sx, sz = (1 if cx < 32 else -1), (1 if cz < 32 else -1)
        for cross in (-4, 4):
            x, z = cx + sx * (8 + cross), cz + sz * (8 - cross)
            lift = int(castle.palace_lift[z + OFFSET_Z, x + OFFSET_X])
            assert all(castle.get(x, y + lift, z) == AIR for y in range(22, 36)), "Tower gap is filled"
    assert tower_tops == [49 + s] * 4
    assert castle.get(31, 79 + m, 31) == castle.get(31, 87 + m, 31) == GOLD
    flag_blocks = castle.blocks[82 + m:87 + m, 31 + OFFSET_Z:34 + OFFSET_Z,
                                32 + OFFSET_X:41 + OFFSET_X]
    assert np.count_nonzero(np.isin(flag_blocks, [RED, YELLOW])) >= 40
    wet_surface = castle.blocks[3] == WATER
    expected_wet = (castle.shoreline > 2) & (castle.shoreline <= 2 + MOAT_WIDTH)
    assert np.array_equal(wet_surface, expected_wet), "Curved moat is interrupted or inconsistent in width"
    assert min(castle.bridge_deck.values()) == 4 and max(castle.bridge_deck.values()) == 7
    for z, y in castle.bridge_deck.items():
        assert abs(y - castle.bridge_deck.get(z - 1, y)) <= 1
        assert all(castle.get(x, head, z) == AIR for x in range(27, 37) for head in (y + 1, y + 2))
    # Flood-fill source water, including under the bridge.
    wet = set(map(tuple, np.argwhere(castle.blocks[:4] == WATER)))
    connected = {next(iter(wet))}
    queue = deque(connected)
    while queue:
        y, z, x = queue.popleft()
        for dy, dz, dx in ((1, 0, 0), (-1, 0, 0), (0, 1, 0), (0, -1, 0), (0, 0, 1), (0, 0, -1)):
            p = (y + dy, z + dz, x + dx)
            if p in wet and p not in connected:
                connected.add(p)
                queue.append(p)
    assert connected == wet
    # Walkability uses two blocks of headroom and stair ascent of at most one.
    # Carpet is passable; fences/bars/glass cannot be used as stair treads.
    passable = {AIR, CARPET, GOLD_CARPET, *BANNERS.values(), LADDER, *PLANTS}
    excluded = {m for m, value in enumerate(MATERIALS)
                if value.name in ("water", "nether_brick_fence", "iron_bars", "spruce_fence", "spruce_fence_gate")}
    walk = set()
    for y in range(3, 36):
        for z in range(Z_MIN, Z_MAX + 1):
            for x in range(X_MIN, X_MAX + 1):
                if (castle.get(x, y, z) not in passable | excluded
                        and castle.get(x, y + 1, z) in passable
                        and castle.get(x, y + 2, z) in passable):
                    walk.add((x, y, z))
    start = (31, 4, Z_MIN)
    assert start in walk
    reached, queue = {start}, deque([start])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            for dy in (-1, 0, 1):
                p = (x + dx, y + dy, z + dz)
                if p in walk and p not in reached:
                    reached.add(p)
                    queue.append(p)
    targets = [(31, 4 + m, 31), (31, 16 + m, 46)]
    for y in (15, 26):
        targets.extend((x, y + m, z) for x, z in ((31, 12), (31, 51), (10, 35), (53, 35), (21, 18), (42, 45)))
    targets.extend((cx, y + s, cz) for y in (4, 15) for cx, cz in TOWERS)
    targets.extend((cx, 4 + s, 31) for cx, _ in ANNEXES)
    targets.extend((cx + (-9 if cx < 32 else 9), 4 + s, 22) for cx, _ in ANNEXES)
    targets.extend((cx + dx, 15 + s, z) for cx, _ in ANNEXES
                   for dx, z in ((-11, 32), (11, 32), (0, 20), (0, 43)))
    targets.extend((cx, 4, cz) for cx, cz in FORTS)
    targets.extend((x, 4, z) for x, z in ((-19, 64), (-19, 71), (82, 64), (82, 71),
                                        (31, 81), (31, 93), (19, -27), (44, -27), (31, -39), (31, -15)))
    for p in targets:
        assert p in reached, ("Unreachable destination", p,
                              "reached_by_y", dict(Counter(q[1] for q in reached)),
                              "is_walkable", p in walk)
    for y in (4 + m, 15 + m, 26 + m):
        level_start = (31, y, 31 if y == 4 + m else 12)
        ring, queue = {level_start}, deque([level_start])
        while queue:
            x, _, z = queue.popleft()
            for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                p = (x + dx, y, z + dz)
                if p in walk and p not in ring:
                    ring.add(p)
                    queue.append(p)
        assert all(p in ring for p in targets if p[1] == y), ("Disconnected gallery or tower entrance", y)
    # The patrol walk must form one connected circuit, including every fort
    # and the bridge over the open main gateway, with access from the court.
    wall_start = (31, 14, GATE_Z)
    assert wall_start in reached, "Wall stairs do not reach the patrol walk"
    patrol, queue = {wall_start}, deque([wall_start])
    while queue:
        x, y, z = queue.popleft()
        for dx, dz in ((1, 0), (-1, 0), (0, 1), (0, -1)):
            p = (x + dx, y, z + dz)
            if p in walk and p not in patrol:
                patrol.add(p)
                queue.append(p)
    assert all((cx, 14, cz) in patrol for cx, cz in FORTS), "Disconnected wall or bastion"
    for angle in np.linspace(0, math.tau, 48, endpoint=False):
        x, z = round(31.5 + WALL_AXES[0] * math.cos(angle)), round(31.5 + WALL_AXES[1] * math.sin(angle))
        assert any((x + dx, 14, z + dz) in patrol for dx in (-1, 0, 1) for dz in (-1, 0, 1)), ("Wall circuit gap", x, z)
    assert len(castle.tree_positions) >= 8 and len(castle.garden_beds) >= 6
    for x, z in castle.courtyard_paths:
        h = int(castle.ground[z + OFFSET_Z, x + OFFSET_X])
        assert (x, h, z) in reached, ("Garden path is obstructed", x, h, z)
    for x, z in castle.tree_positions:
        h = int(castle.ground[z + OFFSET_Z, x + OFFSET_X])
        assert castle.get(x, h, z) == GRASS
        assert castle.get(x, h + 1, z) in (OAK_LOG, BIRCH_LOG)
    assert m - s == 2
    assert all(castle.palace_lift[z + OFFSET_Z, x + OFFSET_X] == s for x, z in TOWERS)
    assert all(castle.palace_lift[31 + OFFSET_Z, x + OFFSET_X] == s for x, _ in ANNEXES)
    assert len(castle.terrain_cells) > 1000
    terrain_heights = sorted({int(castle.ground[z + OFFSET_Z, x + OFFSET_X]) for x, z in castle.terrain_cells})
    assert min(terrain_heights) < 4 < max(terrain_heights)
    for x, y, z in castle.chest_positions:
        assert castle.blocks[y, z, x] == CHEST and castle.blocks[y + 1, z, x] == AIR
    assert np.count_nonzero(castle.blocks == STALL_GATE) == 3
    cz = FOUNTAIN[1]
    eagle = castle.blocks[12:34, cz - 1 + OFFSET_Z:cz + 3 + OFFSET_Z, 14 + OFFSET_X:50 + OFFSET_X]
    assert set(map(int, np.unique(eagle))) <= {AIR, QUARTZ, ANDESITE}, "Eagle must have no colored facial details"
    assert np.any(eagle[:, :, 0] != AIR) and np.any(eagle[:, :, -1] != AIR), "Eagle wingspan was reduced"
    for row in range(22):
        for column in range(36):
            expected = YELLOW if castle.crest_mask[row, column] else AIR
            for z in (cz - 1, cz):
                assert castle.get(14 + column, 57 - row, z) == expected, "Crest differs from reference silhouette"
    assert castle.get(31, 18, cz - 4) == AIR, "Old chest shield remains"
    for x in range(22, 42):
        for z in range(-37, -16):
            if castle.get(x, 5, z) == WATER:
                assert castle.get(x, 4, z) != AIR
    counts = Counter(MATERIALS[int(i)].name for i in castle.blocks.flatten())
    gold_reduction = 1 - counts["gold_block"] / PREVIOUS_GOLD_BLOCKS
    assert counts["gold_block"] <= 50 and gold_reduction > 0.9
    light_counts = {str(floor): int(np.count_nonzero(castle.blocks[floor:floor + 11] == GLOW))
                    for floor in (4 + m, 15 + m, 26 + m)}
    assert all(count >= 60 for count in light_counts.values()), ("Insufficient distributed lighting", light_counts)
    report = {
        "minecraft_version": "1.20.1", "data_version": DATA_VERSION,
        "size_xyz": [WIDTH, HEIGHT, LENGTH], "non_air_blocks": WIDTH * HEIGHT * LENGTH - counts["air"],
        "explicit_air_blocks": counts["air"], "banner_block_entities": len(castle.banner_positions),
        "floor_surface_y": [4 + m, 15 + m, 26 + m], "circular_plaza_diameter": 37,
        "atrium_opening_diameter": 32, "water_source_blocks": len(wet),
        "throne_floor": 2, "throne_seat_xyz": [31 + OFFSET_X, 17 + m, 47 + OFFSET_Z],
        "throne_size_xyz": [4, 4, 2],
        "throne_balcony_bounds_xz": [28 + OFFSET_X, 43 + OFFSET_Z, 35 + OFFSET_X, 50 + OFFSET_Z],
        "second_floor_walls": "open arcades with piers and low railings",
        "ground_and_second_floor_tower_entrances": 8,
        "light_blocks_by_floor_surface_y": light_counts,
        "roof_materials": [MATERIALS[ROOF].name, MATERIALS[ROOF_EDGE].name],
        "tower_centers_xz": [[x + OFFSET_X, z + OFFSET_Z] for x, z in TOWERS],
        "tower_highest_y": tower_tops, "main_roof_highest_y": 79 + m, "flagpole_highest_y": 87 + m,
        "tower_to_hall_wall_gap_approx": 8, "moat_width": MOAT_WIDTH,
        "bridge_clear_width": 10, "bridge_rise": 3,
        "annexes": [{"purpose": purpose, "center_xz": [cx + OFFSET_X, 31.5 + OFFSET_Z],
                     "body_shape": "circular", "body_diameter": 31, "external_entrances": 0,
                     "floor_surface_y": [4 + s, 15 + s], "highest_y": top}
                    for (cx, purpose), top in zip(ANNEXES, annex_tops)],
        "gold_blocks_previous": PREVIOUS_GOLD_BLOCKS, "gold_blocks_current": counts["gold_block"],
        "gold_reduction_percent": round(gold_reduction * 100, 2),
        "stairs": "open underneath with thin top-slab stringers; no solid vertical infill",
        "curtain_wall": {"shape": "ellipse", "semiaxes_xz": list(WALL_AXES),
                         "thickness": 5, "walk_surface_y": 14, "battlement_top_y": 16,
                         "bastions": 6, "bastion_top_y": 24, "patrol_walk_reachable_cells": len(patrol)},
        "moat_shape": "continuous rounded offset following the curtain wall and bastions",
        "elevation": {"main_hall_lift": m, "annex_and_tower_lift": s, "main_hall_extra_height": m - s},
        "terrain": {"seed": 12012026, "changed_lawn_columns": len(castle.terrain_cells), "surface_y_values": terrain_heights},
        "service_buildings": {"warehouse_chests": len(castle.chest_positions), "stable_stalls": 3,
                              "training_yard_size_xz": [32, 22], "archery_targets": 2, "training_dummies": 2},
        "fountain": {"center_xz": [FOUNTAIN[0] + OFFSET_X, FOUNTAIN[1] + OFFSET_Z], "basin_diameter": 20,
                     "plaza_diameter": 30, "sculpture": "abstract spread-wing eagle without eyes, beak or talons",
                     "eagle_wingspan": 36, "eagle_bounds_y": [12, 33],
                     "crest_position": "above eagle", "crest_size_xy": [36, 22], "crest_bounds_y": [36, 57],
                     "crest_reference": "royal_crest_reference.png", "crest_material": "yellow_wool", "gold_added": 0},
        "gardens": {"trees": len(castle.tree_positions), "flower_beds": len(castle.garden_beds),
                    "tree_species": ["oak", "birch"], "paved_path_blocks": len(castle.courtyard_paths),
                    "flowers": sum(counts[MATERIALS[state].name] for state in FLOWERS)},
        "checks": ["SNBT parse round trip", "structure NBT round trip", "MCEdit NBT arrays round trip",
                   "1.20.1 asset block names and rendered state values", "banner attachments and lower clearance",
                   f"{WIDTH} x {HEIGHT} x {LENGTH} bounds", "open entrance", "central atrium", "continuous moat",
                   "walkable bridge, throne and both ring galleries", "continuous gallery circuits at each floor",
                   "compact throne on second floor and unobstructed ground plaza", "open second-floor arcades",
                   "ground and second-floor paths to every tower", "all stair treads and headroom",
                   "distributed interior lighting", "dark brown wooden roofs", "separate lower towers",
                   "eight-block-wide moat", "arched bridge headroom and continuous ascent", "gilded spire and rooftop flag",
                   "two smaller separated annexes", "both annex storeys reachable through side passages",
                   "all six interior stair flights open underneath", "gold block reduction exceeds 90 percent",
                   "circular annex shells with no exterior entrances", "continuous accessible curtain-wall patrol walk",
                   "six bastions accessible from the inner court and wall walk", "rounded eight-block moat follows fortifications",
                   "unobstructed garden paths and planted trees on grass", "main hall plinth two blocks above the other palace buildings",
                   "raised connecting galleries and terrace steps are reachable", "seeded lawn hills and hollows",
                   "warehouse chests with lid clearance", "three stable stalls with water and hay", "training yard and fountain plaza reachable",
                   "36-block abstract eagle without facial details", "36-by-22 crest above eagle matches reference alpha silhouette"],
        "materials": dict(counts),
    }
    return report


def render(castle, filename, *, cut=False, bounds=None):
    # Orthographic, painter-sorted exposed voxel faces from the exported array.
    # Remove each building's front independently to keep both annexes visible.
    b = castle.blocks.copy()
    if cut:
        b[10:, :19 + OFFSET_Z, 6 + OFFSET_X:58 + OFFSET_X] = AIR
        b[10:, :, 8 + OFFSET_X:11 + OFFSET_X] = AIR
        for cx, _ in ANNEXES:
            b[29:, :, cx - 16 + OFFSET_X:cx + 17 + OFFSET_X] = AIR
            b[8:, :23 + OFFSET_Z, cx - 16 + OFFSET_X:cx + 17 + OFFSET_X] = AIR
            b[8:, 15 + OFFSET_Z:49 + OFFSET_Z, cx - 16 + OFFSET_X:cx - 10 + OFFSET_X] = AIR
        b[43:, :, :] = AIR
        b[5:, :GATE_Z + 4 + OFFSET_Z, :] = AIR
    if bounds:
        x1, z1, x2, z2 = bounds
        b = b[:, z1 + OFFSET_Z:z2 + OFFSET_Z + 1, x1 + OFFSET_X:x2 + OFFSET_X + 1]
    occupied = np.argwhere(b != AIR)
    visible_height, visible_length, visible_width = b.shape
    canvas = Image.new("RGB", (2600, 1500), (233, 239, 242))
    draw = ImageDraw.Draw(canvas)
    depth = float(np.max(occupied[:, 0] + 1 + (occupied[:, 1] + occupied[:, 2]) * 0.43))
    scale, oy = min(2480 / (visible_width + visible_length), 1200 / depth), 1380
    ox = (2600 - (visible_width - visible_length) * scale) / 2

    def project(x, y, z):
        return (round(ox + (x - z) * scale), round(oy - (x + z) * scale * 0.43 - y * scale))

    occupied = sorted(occupied, key=lambda p: (int(p[0] - p[1] - p[2]), int(p[0])))
    for y, z, x in occupied:
        y, z, x = int(y), int(z), int(x)
        state = int(b[y, z, x])
        mat = MATERIALS[state]
        color = mat.color
        variation = ((x * 17 + z * 31 + y * 11) % 11) - 5
        color = tuple(max(0, min(255, c + variation)) for c in color)
        low, high = y, y + 1
        if mat.name.endswith("carpet"):
            high = y + 0.08
        elif state in PLANTS:
            high = y + 0.65
        elif mat.name.endswith("slab"):
            if dict(mat.properties).get("type") == "top":
                low = y + 0.5
            else:
                high = y + 0.5
        # Thin details are narrow prisms in this architectural preview.
        inset = 0.32 if mat.name in ("iron_bars", "nether_brick_fence", "spruce_fence", "ladder") else 0
        if state in PLANTS:
            inset = 0.3
        xmin, xmax, zmin, zmax = x + inset, x + 1 - inset, z + inset, z + 1 - inset
        faces = []
        if z == 0 or b[y, z - 1, x] == AIR:
            faces.append((0.72, [(xmin, low, zmin), (xmax, low, zmin), (xmax, high, zmin), (xmin, high, zmin)]))
        if x == 0 or b[y, z, x - 1] == AIR:
            faces.append((0.88, [(xmin, low, zmin), (xmin, low, zmax), (xmin, high, zmax), (xmin, high, zmin)]))
        if y == visible_height - 1 or b[y + 1, z, x] == AIR:
            faces.append((1.07, [(xmin, high, zmin), (xmax, high, zmin), (xmax, high, zmax), (xmin, high, zmax)]))
        for shade, points in faces:
            tint = tuple(min(255, round(c * shade)) for c in color)
            border = tuple(max(0, c - 13) for c in tint)
            draw.polygon([project(*p) for p in points], fill=tint, outline=border)
    font_path = Path("C:/Windows/Fonts/arial.ttf")
    font = ImageFont.truetype(str(font_path), 35) if font_path.exists() else ImageFont.load_default()
    small = ImageFont.truetype(str(font_path), 20) if font_path.exists() else ImageFont.load_default()
    view = "EAGLE FOUNTAIN" if bounds else "SECTION" if cut else "EXTERIOR"
    draw.text((55, 40), "ROYAL ESTATE / " + view, fill=(38, 51, 58), font=font)
    draw.text((57, 92), f"Minecraft 1.20.1   |   {WIDTH} x {HEIGHT} x {LENGTH}   |   Actual block geometry",
              fill=(76, 91, 99), font=small)
    draw.text((57, 1440), "Architectural voxel preview; glass shown opaque, stairs simplified. Not an in-game screenshot.",
              fill=(76, 91, 99), font=small)
    canvas.save(OUT / filename)


def render_site_plan(castle):
    top = HEIGHT - 1 - np.argmax(castle.blocks[::-1] != AIR, axis=0)
    zs, xs = np.indices(top.shape)
    palette = np.array([mat.color for mat in MATERIALS], dtype=np.uint8)
    pixels = palette[castle.blocks[top, zs, xs]]
    shade = np.clip(1 + (top.astype(float) - np.roll(top, 1, 0)) * 0.11, 0.72, 1.2)
    pixels = np.clip(pixels * shade[:, :, None], 0, 255).astype(np.uint8)
    plan = Image.fromarray(pixels).resize((WIDTH * 7, LENGTH * 7), Image.Resampling.NEAREST)
    canvas = Image.new("RGB", (1800, 1820), (233, 239, 242))
    canvas.paste(plan, (144, 160))
    draw = ImageDraw.Draw(canvas)
    font = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 32)
    small = ImageFont.truetype("C:/Windows/Fonts/arial.ttf", 22)
    draw.text((100, 35), "ROYAL ESTATE / SITE PLAN", fill=(38, 51, 58), font=font)
    draw.text((100, 85), "North / main entrance at top. Actual exported block geometry.", fill=(76, 91, 99), font=small)
    draw.text((100, 1740), "Raised palace  |  Eagle fountain  |  Warehouse  |  Training yard  |  Stables  |  Rolling gardens", fill=(76, 91, 99), font=small)
    canvas.save(OUT / "site_plan.png")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--client-jar", type=Path, default=Path.home() /
                        ".gradle/caches/forge_gradle/minecraft_repo/versions/1.20.1/client.jar")
    args = parser.parse_args()
    print("Building castle geometry...", flush=True)
    castle = Castle().build()
    print("Checking architecture, circulation and materials...", flush=True)
    report = validate_geometry(castle)
    print("Writing schematic, SNBT, structure NBT and datapack...", flush=True)
    structure = castle.export()
    validate_exports(castle, structure, args.client_jar)
    report["sha256"] = {p.name: hashlib.sha256(p.read_bytes()).hexdigest()
                        for p in [OUT / (NAME + suffix) for suffix in (".schematic", ".snbt", ".nbt")]
                        + [OUT / "royal_main_hall_datapack.zip"]}
    (OUT / "validation.json").write_text(json.dumps(report, indent=2) + "\n", encoding="utf-8")
    print("Rendering exterior and cutaway...", flush=True)
    render(castle, "exterior.png")
    render(castle, "interior_section.png", cut=True)
    render(castle, "fountain_detail.png", bounds=(10, -43, 53, -11))
    render_site_plan(castle)
    print(json.dumps({k: report[k] for k in ("size_xyz", "non_air_blocks", "banner_block_entities", "checks")}, indent=2))
    print("Output:", OUT)


if __name__ == "__main__":
    main()
