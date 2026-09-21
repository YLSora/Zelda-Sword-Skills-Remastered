"""Split the supplied vanilla NBT into sparse 16x96x16 worldgen templates.

Uses the same nbtlib dependency as build_castle.py. Air is cleared per terrain
column at runtime, so only the original non-air blocks need to be packaged.
"""
from pathlib import Path
import hashlib
import json
import sys

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT / "build/castle-tools"))
import nbtlib
from nbtlib import Compound, File, Int, List


def main():
    source = ROOT / "artifacts/royal_main_hall/royal_estate_216x96x216.nbt"
    out = ROOT / "src/main/resources/data/zeldaswordskills_remastered/structures/royal_castle"
    original = nbtlib.load(source)
    assert list(original["size"]) == [216, 96, 216]
    assert not original["entities"]
    palette = original["palette"]
    air = {i for i, state in enumerate(palette) if str(state["Name"]) == "minecraft:air"}
    tiles = {(x, z): List[Compound]() for x in range(14) for z in range(14)}
    blocks = entities = 0
    for entry in original["blocks"]:
        if int(entry["state"]) in air:
            continue
        x, y, z = map(int, entry["pos"])
        block = Compound(entry)
        block["pos"] = List[Int]([x % 16, y, z % 16])
        tiles[x // 16, z // 16].append(block)
        blocks += 1
        entities += "nbt" in block
    assert blocks == 338596 and entities == 30
    out.mkdir(parents=True, exist_ok=True)
    for (x, z), entries in tiles.items():
        template = File({"DataVersion": Int(3465),
                         "size": List[Int]([min(16, 216 - x * 16), 96, min(16, 216 - z * 16)]),
                         "palette": palette, "blocks": entries, "entities": List[Compound]()})
        target = out / f"tile_{x}_{z}.nbt"
        template.save(target, gzipped=True)
        assert nbtlib.load(target) == template
    report = {"source_sha256": hashlib.sha256(source.read_bytes()).hexdigest(),
              "tiles": len(tiles), "non_air_blocks": blocks, "block_entities": entities,
              "compressed_bytes": sum(p.stat().st_size for p in out.glob("*.nbt"))}
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    main()
