package io.github.naimjeg.obeliskdepths.dungeon.template;

import io.github.naimjeg.obeliskdepths.dungeon.corridor.DungeonCorridorDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.DungeonRoomDefinition;
import io.github.naimjeg.obeliskdepths.dungeon.room.RoomConnectorDefinition;
import io.github.naimjeg.obeliskdepths.worldgen.structure.layout.DungeonRoomFootprint;
import java.io.BufferedInputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.zip.GZIPInputStream;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;

public final class DungeonTemplateResourceValidator {
    private DungeonTemplateResourceValidator() {
    }

    public record Size(int x, int y, int z) {
        public Size {
            if (x <= 0 || y <= 0 || z <= 0) {
                throw new IllegalArgumentException(
                        "Template size must be positive: " + x + "x" + y + "x" + z
                );
            }
        }
    }

    public static List<String> validateRooms(
            Map<Identifier, DungeonRoomDefinition> rooms,
            ResourceManager resourceManager
    ) {
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Identifier, DungeonRoomDefinition> entry : rooms.entrySet()) {
            Size size = readResourceSize(resourceManager, entry.getValue().template(), errors);

            if (size != null) {
                errors.addAll(validateRoom(entry.getKey(), entry.getValue(), size));
            }
        }

        return errors;
    }

    public static List<String> validateCorridors(
            Map<Identifier, DungeonCorridorDefinition> corridors,
            ResourceManager resourceManager
    ) {
        List<String> errors = new ArrayList<>();

        for (Map.Entry<Identifier, DungeonCorridorDefinition> entry : corridors.entrySet()) {
            Size size = readResourceSize(resourceManager, entry.getValue().template(), errors);

            if (size != null) {
                errors.addAll(validateCorridor(entry.getKey(), entry.getValue(), size));
            }
        }

        return errors;
    }

    public static List<String> validateRoom(
            Identifier id,
            DungeonRoomDefinition room,
            Size templateSize
    ) {
        List<String> errors = new ArrayList<>();
        validateTemplateEnvelope(
                "dungeon room",
                id,
                room.template(),
                room.footprint(),
                room.templateOffset(),
                templateSize,
                errors
        );
        validatePortsInsideTemplate(
                "dungeon room",
                id,
                room.ports(),
                room.templateOffset(),
                templateSize,
                errors
        );
        return errors;
    }

    public static List<String> validateCorridor(
            Identifier id,
            DungeonCorridorDefinition corridor,
            Size templateSize
    ) {
        List<String> errors = new ArrayList<>();
        validateTemplateEnvelope(
                "dungeon corridor",
                id,
                corridor.template(),
                corridor.footprint(),
                BlockPos.ZERO,
                templateSize,
                errors
        );
        validatePortsInsideTemplate(
                "dungeon corridor",
                id,
                corridor.ports(),
                BlockPos.ZERO,
                templateSize,
                errors
        );
        return errors;
    }

    public static List<String> validateAllSuppliedTemplatesReferenced(
            Map<Identifier, DungeonRoomDefinition> rooms,
            Map<Identifier, DungeonCorridorDefinition> corridors
    ) {
        Set<Identifier> referenced = new HashSet<>();

        rooms.values().forEach(room -> referenced.add(room.template()));
        corridors.values().forEach(corridor -> referenced.add(corridor.template()));

        List<String> errors = new ArrayList<>();

        for (Identifier template : BuiltinDungeonTemplates.ALL_SUPPLIED_TEMPLATES) {
            if (!referenced.contains(template)) {
                errors.add("supplied dungeon template is not referenced: " + template);
            }
        }

        return errors;
    }

    public static Path templatePath(
            Path resourcesRoot,
            Identifier template
    ) {
        return resourcesRoot
                .resolve("data")
                .resolve(template.getNamespace())
                .resolve("structure")
                .resolve(template.getPath() + ".nbt");
    }

    public static Size readTemplateSize(Path path) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return readSize(input);
        }
    }

    private static Size readResourceSize(
            ResourceManager resourceManager,
            Identifier template,
            List<String> errors
    ) {
        Identifier resourceId = Identifier.fromNamespaceAndPath(
                template.getNamespace(),
                "structure/" + template.getPath() + ".nbt"
        );

        return resourceManager.getResource(resourceId)
                .map(resource -> {
                    try (InputStream input = resource.open()) {
                        return readSize(input);
                    } catch (IOException exception) {
                        errors.add("failed to read dungeon template "
                                + template + ": " + exception.getMessage());
                        return null;
                    }
                })
                .orElseGet(() -> {
                    errors.add("missing dungeon template resource: " + template);
                    return null;
                });
    }

    private static void validateTemplateEnvelope(
            String kind,
            Identifier id,
            Identifier template,
            DungeonRoomFootprint footprint,
            BlockPos templateOffset,
            Size templateSize,
            List<String> errors
    ) {
        int maxX = templateOffset.getX() + templateSize.x();
        int maxY = templateOffset.getY() + templateSize.y();
        int maxZ = templateOffset.getZ() + templateSize.z();

        if (templateOffset.getX() < 0
                || templateOffset.getY() < 0
                || templateOffset.getZ() < 0
                || maxX > footprint.widthBlocks()
                || maxY > footprint.heightBlocks()
                || maxZ > footprint.depthBlocks()) {
            errors.add(kind + " " + id + " template " + template
                    + " with offset " + templateOffset
                    + " and size " + templateSize
                    + " does not fit footprint envelope "
                    + footprint.widthBlocks() + "x"
                    + footprint.heightBlocks() + "x"
                    + footprint.depthBlocks());
        }
    }

    private static void validatePortsInsideTemplate(
            String kind,
            Identifier id,
            List<RoomConnectorDefinition> ports,
            BlockPos templateOffset,
            Size templateSize,
            List<String> errors
    ) {
        for (RoomConnectorDefinition port : ports) {
            BlockPos opening = port.openingMin();

            if (opening.getX() < templateOffset.getX()
                    || opening.getY() < templateOffset.getY()
                    || opening.getZ() < templateOffset.getZ()
                    || opening.getX() >= templateOffset.getX() + templateSize.x()
                    || opening.getY() >= templateOffset.getY() + templateSize.y()
                    || opening.getZ() >= templateOffset.getZ() + templateSize.z()) {
                errors.add(kind + " " + id + " port " + port.id()
                        + " opening_min "
                        + opening
                        + " is outside template offset "
                        + templateOffset
                        + " size "
                        + templateSize);
            }
        }
    }

    private static Size readSize(InputStream input) throws IOException {
        try (DataInputStream data = new DataInputStream(decompressIfNeeded(input))) {
            CompoundTag root = NbtIo.read(data, NbtAccounter.unlimitedHeap());

            if (root == null) {
                throw new IOException("Structure NBT root is missing");
            }

            Tag rawSize = root.get("size");

            if (!(rawSize instanceof ListTag size)
                    || size.size() != 3
                    || !(size.get(0) instanceof IntTag)
                    || !(size.get(1) instanceof IntTag)
                    || !(size.get(2) instanceof IntTag)) {
                throw new IOException("Structure NBT requires size:[I;x,y,z]");
            }

            return new Size(
                    size.get(0).asInt().orElseThrow(),
                    size.get(1).asInt().orElseThrow(),
                    size.get(2).asInt().orElseThrow()
            );
        }
    }

    private static InputStream decompressIfNeeded(InputStream input)
            throws IOException {
        BufferedInputStream buffered = new BufferedInputStream(input);
        buffered.mark(2);

        int first = buffered.read();
        int second = buffered.read();

        buffered.reset();

        return first == 0x1F && second == 0x8B
                ? new GZIPInputStream(buffered)
                : buffered;
    }
}
