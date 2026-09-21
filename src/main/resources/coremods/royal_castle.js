function initializeCoreMod() {
    var ASMAPI = Java.type('net.minecraftforge.coremod.api.ASMAPI');
    var Opcodes = Java.type('org.objectweb.asm.Opcodes');
    var InsnList = Java.type('org.objectweb.asm.tree.InsnList');
    var VarInsnNode = Java.type('org.objectweb.asm.tree.VarInsnNode');
    var InsnNode = Java.type('org.objectweb.asm.tree.InsnNode');
    var JumpInsnNode = Java.type('org.objectweb.asm.tree.JumpInsnNode');
    var LabelNode = Java.type('org.objectweb.asm.tree.LabelNode');
    var MethodInsnNode = Java.type('org.objectweb.asm.tree.MethodInsnNode');
    var hooks = 'zeldaswordskills_remastered/worldgen/RoyalCastleHooks';
    return {
        'protect_castle_from_cross_chunk_features': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.world.level.levelgen.placement.PlacedFeature',
                'methodName': ASMAPI.mapMethod('m_226377_'),
                'methodDesc': '(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z'
            },
            'transformer': function(method) {
                var proceed = new LabelNode();
                var guard = new InsnList();
                guard.add(new VarInsnNode(Opcodes.ALOAD, 1));
                guard.add(new VarInsnNode(Opcodes.ALOAD, 4));
                guard.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hooks, 'skipFeatures',
                    '(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/core/BlockPos;)Z', false));
                guard.add(new JumpInsnNode(Opcodes.IFEQ, proceed));
                guard.add(new InsnNode(Opcodes.ICONST_0));
                guard.add(new InsnNode(Opcodes.IRETURN));
                guard.add(proceed);
                method.instructions.insert(guard);
                return method;
            }
        },
        'exclude_nearby_structure_starts': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.world.level.levelgen.structure.Structure',
                'methodName': ASMAPI.mapMethod('m_226596_'),
                'methodDesc': '(Lnet/minecraft/core/RegistryAccess;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/world/level/biome/BiomeSource;Lnet/minecraft/world/level/levelgen/RandomState;Lnet/minecraft/world/level/levelgen/structure/templatesystem/StructureTemplateManager;JLnet/minecraft/world/level/ChunkPos;ILnet/minecraft/world/level/LevelHeightAccessor;Ljava/util/function/Predicate;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;'
            },
            'transformer': function(method) {
                var instructions = method.instructions.toArray();
                for (var i = 0; i < instructions.length; i++) {
                    if (instructions[i].getOpcode() === Opcodes.ARETURN) {
                        var call = new InsnList();
                        call.add(new VarInsnNode(Opcodes.ALOAD, 4));
                        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hooks, 'filterStart',
                            '(Lnet/minecraft/world/level/levelgen/structure/StructureStart;Lnet/minecraft/world/level/levelgen/RandomState;)Lnet/minecraft/world/level/levelgen/structure/StructureStart;', false));
                        method.instructions.insertBefore(instructions[i], call);
                    }
                }
                return method;
            }
        },
        'locate_unique_castle': {
            'target': {
                'type': 'METHOD',
                'class': 'net.minecraft.world.level.chunk.ChunkGenerator',
                'methodName': ASMAPI.mapMethod('m_223037_'),
                'methodDesc': '(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;IZ)Lcom/mojang/datafixers/util/Pair;'
            },
            'transformer': function(method) {
                var instructions = method.instructions.toArray();
                for (var i = 0; i < instructions.length; i++) {
                    if (instructions[i].getOpcode() === Opcodes.ARETURN) {
                        var call = new InsnList();
                        call.add(new VarInsnNode(Opcodes.ALOAD, 1));
                        call.add(new VarInsnNode(Opcodes.ALOAD, 2));
                        call.add(new VarInsnNode(Opcodes.ALOAD, 3));
                        call.add(new MethodInsnNode(Opcodes.INVOKESTATIC, hooks, 'locate',
                            '(Lcom/mojang/datafixers/util/Pair;Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/HolderSet;Lnet/minecraft/core/BlockPos;)Lcom/mojang/datafixers/util/Pair;', false));
                        method.instructions.insertBefore(instructions[i], call);
                    }
                }
                return method;
            }
        }
    };
}
