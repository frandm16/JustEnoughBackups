package com.frandm.justenoughbackups.backup.integrity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.StreamTagVisitor;
import net.minecraft.nbt.TagType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.storage.RegionFileVersion;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class McaIntegrityValidator {
    private static final Pattern REGION_FILE_PATTERN = Pattern.compile("^r\\.(-?\\d+)\\.(-?\\d+)\\.mca$");
    private static final int SECTOR_BYTES = 4096;
    private static final int HEADER_BYTES = 8192;
    private static final int ENTRIES_COUNT = 1024;

    private McaIntegrityValidator() {
    }

    public enum McaType {
        CHUNK,
        ENTITIES,
        POI,
        OTHER
    }

    public record MisplacedChunk(
            String relativePath,
            int sectorIndex,
            int expectedChunkX,
            int expectedChunkZ,
            int actualChunkX,
            int actualChunkZ
    ) {
        public String formatMessage() {
            return String.format(
                    Locale.ROOT,
                    "Misplaced chunk in %s (index %d): expected (%d, %d), found (%d, %d)",
                    relativePath, sectorIndex, expectedChunkX, expectedChunkZ, actualChunkX, actualChunkZ
            );
        }
    }

    public record ValidationReport(
            List<MisplacedChunk> misplacedChunks,
            List<String> errors
    ) {
        public boolean isValid() {
            return misplacedChunks.isEmpty() && errors.isEmpty();
        }

        public String formatErrors() {
            StringBuilder sb = new StringBuilder();
            for (MisplacedChunk chunk : misplacedChunks) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append("- ").append(chunk.formatMessage());
            }
            for (String error : errors) {
                if (!sb.isEmpty()) {
                    sb.append("\n");
                }
                sb.append("- ").append(error);
            }
            return sb.toString();
        }
    }

    public interface McaByteSource {
        long length() throws IOException;
        void readFully(long position, byte[] dest, int offset, int length) throws IOException;

        static McaByteSource fromChannel(FileChannel channel) {
            return new McaByteSource() {
                @Override
                public long length() throws IOException {
                    return channel.size();
                }

                @Override
                public void readFully(long position, byte[] dest, int offset, int length) throws IOException {
                    ByteBuffer buffer = ByteBuffer.wrap(dest, offset, length);
                    long currentPos = position;
                    while (buffer.hasRemaining()) {
                        int read = channel.read(buffer, currentPos);
                        if (read < 0) {
                            throw new EOFException("Premature EOF reading MCA at position " + currentPos);
                        }
                        currentPos += read;
                    }
                }
            };
        }

        static McaByteSource fromBytes(byte[] bytes) {
            return new McaByteSource() {
                @Override
                public long length() {
                    return bytes.length;
                }

                @Override
                public void readFully(long position, byte[] dest, int offset, int length) throws IOException {
                    if (position < 0 || position + length > bytes.length) {
                        throw new EOFException("Premature EOF reading MCA byte array at position " + position);
                    }
                    System.arraycopy(bytes, (int) position, dest, offset, length);
                }
            };
        }
    }

    public static boolean isMcaFile(String name) {
        if (name == null || !name.endsWith(".mca")) {
            return false;
        }
        int slash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        String fileName = slash >= 0 ? name.substring(slash + 1) : name;
        return REGION_FILE_PATTERN.matcher(fileName).matches();
    }

    public static McaType detectType(String relativePath) {
        String normalized = relativePath.replace('\\', '/').toLowerCase(Locale.ROOT);
        if (normalized.contains("/region/") || normalized.startsWith("region/")) {
            return McaType.CHUNK;
        }
        if (normalized.contains("/entities/") || normalized.startsWith("entities/")) {
            return McaType.ENTITIES;
        }
        if (normalized.contains("/poi/") || normalized.startsWith("poi/")) {
            return McaType.POI;
        }
        return McaType.OTHER;
    }

    public static ValidationReport validateFile(Path file, String relativePath) {
        try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
            return validate(McaByteSource.fromChannel(channel), relativePath, file.getFileName().toString());
        } catch (IOException e) {
            return new ValidationReport(List.of(), List.of("Failed to open " + relativePath + ": " + e.getMessage()));
        }
    }

    public static ValidationReport validateBytes(byte[] bytes, String relativePath) {
        int slash = Math.max(relativePath.lastIndexOf('/'), relativePath.lastIndexOf('\\'));
        String fileName = slash >= 0 ? relativePath.substring(slash + 1) : relativePath;
        try {
            return validate(McaByteSource.fromBytes(bytes), relativePath, fileName);
        } catch (IOException e) {
            return new ValidationReport(List.of(), List.of("Failed to read " + relativePath + ": " + e.getMessage()));
        }
    }

    public static ValidationReport validateZipArchive(Path zipFile) throws IOException {
        return validateZipArchive(zipFile, () -> {});
    }

    public static ValidationReport validateZipArchive(Path zipFile, Runnable onActivity) throws IOException {
        List<MisplacedChunk> misplaced = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Runnable heartbeat = onActivity == null ? () -> {} : onActivity;
        try (ZipFile zip = new ZipFile(zipFile.toFile())) {
            Enumeration<ZipArchiveEntry> entries = zip.getEntries();
            while (entries.hasMoreElements()) {
                ZipArchiveEntry entry = entries.nextElement();
                if (entry.isDirectory()) {
                    continue;
                }
                String name = entry.getName();
                if (isMcaFile(name)) {
                    heartbeat.run();
                    try (InputStream in = zip.getInputStream(entry)) {
                        byte[] bytes = in.readAllBytes();
                        ValidationReport entryReport = validateBytes(bytes, name);
                        misplaced.addAll(entryReport.misplacedChunks());
                        errors.addAll(entryReport.errors());
                    } catch (Exception e) {
                        errors.add("Failed to read " + name + " from ZIP " + zipFile.getFileName() + ": " + e.getMessage());
                    }
                    heartbeat.run();
                }
            }
        }
        return new ValidationReport(Collections.unmodifiableList(misplaced), Collections.unmodifiableList(errors));
    }

    public static ValidationReport validateWorldDirectory(Path worldPath, List<String> excludedPaths) throws IOException {
        return validateWorldDirectory(worldPath, excludedPaths, () -> {});
    }

    public static ValidationReport validateWorldDirectory(Path worldPath, List<String> excludedPaths, Runnable onActivity) throws IOException {
        List<MisplacedChunk> misplaced = new ArrayList<>();
        List<String> errors = new ArrayList<>();
        Runnable heartbeat = onActivity == null ? () -> {} : onActivity;
        if (!Files.isDirectory(worldPath)) {
            return new ValidationReport(List.of(), List.of());
        }
        List<Path> mcaFiles = new ArrayList<>();
        Files.walkFileTree(worldPath, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                heartbeat.run();
                Path relative = worldPath.relativize(dir);
                if (shouldSkip(relative, excludedPaths)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                Path relative = worldPath.relativize(file);
                if (!shouldSkip(relative, excludedPaths) && isMcaFile(file.getFileName().toString())) {
                    mcaFiles.add(file);
                }
                return FileVisitResult.CONTINUE;
            }
        });

        for (Path file : mcaFiles) {
            heartbeat.run();
            String relativeName = worldPath.relativize(file).toString().replace('\\', '/');
            ValidationReport fileReport = validateFile(file, relativeName);
            misplaced.addAll(fileReport.misplacedChunks());
            errors.addAll(fileReport.errors());
            heartbeat.run();
        }

        return new ValidationReport(Collections.unmodifiableList(misplaced), Collections.unmodifiableList(errors));
    }

    private static boolean shouldSkip(Path relativePath, List<String> excludedPaths) {
        String normalized = relativePath.toString().replace('\\', '/').toLowerCase(Locale.ROOT);
        if (excludedPaths != null) {
            for (String path : excludedPaths) {
                String normalizedExcluded = path.toLowerCase(Locale.ROOT);
                if (normalized.equals(normalizedExcluded) || normalized.startsWith(normalizedExcluded + "/")) {
                    return true;
                }
            }
        }
        for (Path part : relativePath) {
            if ("backups".equalsIgnoreCase(part.toString())) {
                return true;
            }
        }
        return false;
    }

    public static ValidationReport validate(McaByteSource source, String relativePath, String fileName) throws IOException {
        Matcher matcher = REGION_FILE_PATTERN.matcher(fileName);
        if (!matcher.matches()) {
            return new ValidationReport(List.of(), List.of());
        }
        int regionX;
        int regionZ;
        try {
            regionX = Integer.parseInt(matcher.group(1));
            regionZ = Integer.parseInt(matcher.group(2));
        } catch (NumberFormatException exception) {
            return new ValidationReport(List.of(), List.of("Invalid region coordinates in filename: " + relativePath));
        }
        McaType mcaType = detectType(relativePath);

        long fileLength = source.length();
        if (fileLength == 0) {
            return new ValidationReport(List.of(), List.of());
        }
        if (fileLength < HEADER_BYTES) {
            return new ValidationReport(List.of(), List.of("MCA file truncated (" + fileLength + " bytes, expected at least " + HEADER_BYTES + "): " + relativePath));
        }

        byte[] headerBytes = new byte[HEADER_BYTES];
        source.readFully(0, headerBytes, 0, HEADER_BYTES);
        ByteBuffer header = ByteBuffer.wrap(headerBytes);

        List<MisplacedChunk> misplaced = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        for (int i = 0; i < ENTRIES_COUNT; i++) {
            int entry = header.getInt(i * 4);
            int sectorOffset = (entry >>> 8) & 0xFFFFFF;
            int sectorCount = entry & 0xFF;

            if (sectorOffset == 0 && sectorCount == 0) {
                continue;
            }

            int expectedChunkX = (regionX * 32) + (i & 31);
            int expectedChunkZ = (regionZ * 32) + (i / 32);

            if (sectorOffset < 2) {
                errors.add(String.format(Locale.ROOT, "Invalid sector offset %d for chunk index %d in %s", sectorOffset, i, relativePath));
                continue;
            }

            long chunkStart = (long) sectorOffset * SECTOR_BYTES;
            if (chunkStart + 5L > fileLength) {
                errors.add(String.format(Locale.ROOT, "Chunk header at sector %d exceeds file size %d for index %d in %s", sectorOffset, fileLength, i, relativePath));
                continue;
            }

            byte[] chunkHead = new byte[5];
            source.readFully(chunkStart, chunkHead, 0, 5);
            int payloadLength = ((chunkHead[0] & 0xFF) << 24)
                    | ((chunkHead[1] & 0xFF) << 16)
                    | ((chunkHead[2] & 0xFF) << 8)
                    | (chunkHead[3] & 0xFF);
            byte compressionByte = chunkHead[4];

            if (payloadLength <= 1) {
                errors.add(String.format(Locale.ROOT, "Invalid chunk payload length %d for index %d in %s", payloadLength, i, relativePath));
                continue;
            }

            if ((long) payloadLength > (long) sectorCount * SECTOR_BYTES - 4L) {
                errors.add(String.format(Locale.ROOT, "Chunk payload length %d exceeds allocated sectors %d for index %d in %s", payloadLength, sectorCount, i, relativePath));
                continue;
            }

            if (chunkStart + 4L + payloadLength > fileLength) {
                errors.add(String.format(Locale.ROOT, "Chunk payload at sector %d (length %d) exceeds file size %d for index %d in %s", sectorOffset, payloadLength, fileLength, i, relativePath));
                continue;
            }

            if ((compressionByte & 128) != 0) {
                continue;
            }

            int versionId = compressionByte & 0xFF;
            if (!RegionFileVersion.isValidVersion(versionId)) {
                errors.add(String.format(Locale.ROOT, "Unsupported compression version %d for chunk index %d in %s", versionId, i, relativePath));
                continue;
            }

            byte[] payload = new byte[payloadLength - 1];
            source.readFully(chunkStart + 5L, payload, 0, payloadLength - 1);

            RegionFileVersion version = RegionFileVersion.fromId(versionId);
            if (version == null) {
                errors.add(String.format(Locale.ROOT, "Null compression version for id %d at index %d in %s", versionId, i, relativePath));
                continue;
            }

            try (InputStream decompressed = version.wrap(new ByteArrayInputStream(payload));
                 DataInputStream dataIn = new DataInputStream(decompressed)) {
                ChunkCoordinateVisitor visitor = new ChunkCoordinateVisitor(mcaType);
                NbtIo.parse(dataIn, visitor, NbtAccounter.unlimitedHeap());

                if (visitor.hasCoordinates()) {
                    int actualChunkX = visitor.getX();
                    int actualChunkZ = visitor.getZ();
                    if (actualChunkX != expectedChunkX || actualChunkZ != expectedChunkZ) {
                        misplaced.add(new MisplacedChunk(relativePath, i, expectedChunkX, expectedChunkZ, actualChunkX, actualChunkZ));
                    }
                } else if (mcaType == McaType.CHUNK || mcaType == McaType.ENTITIES) {
                    errors.add(String.format(Locale.ROOT, "Missing chunk coordinate NBT tag at index %d in %s", i, relativePath));
                }
            } catch (Exception e) {
                errors.add(String.format(Locale.ROOT, "Corrupted chunk NBT at index %d in %s: %s", i, relativePath, e.getMessage()));
            }
        }

        return new ValidationReport(Collections.unmodifiableList(misplaced), Collections.unmodifiableList(errors));
    }

    private static final class ChunkCoordinateVisitor implements StreamTagVisitor {
        private final McaType mcaType;
        private Integer foundX;
        private Integer foundZ;
        private String currentEntry = "";
        private int depth = 0;

        ChunkCoordinateVisitor(McaType mcaType) {
            this.mcaType = mcaType;
        }

        boolean hasCoordinates() {
            return foundX != null && foundZ != null;
        }

        int getX() {
            return foundX != null ? foundX : 0;
        }

        int getZ() {
            return foundZ != null ? foundZ : 0;
        }

        @Override
        public ValueResult visitRootEntry(TagType<?> type) {
            depth = 0;
            return ValueResult.CONTINUE;
        }

        @Override
        public EntryResult visitEntry(TagType<?> type) {
            return EntryResult.ENTER;
        }

        @Override
        public EntryResult visitEntry(TagType<?> type, String name) {
            currentEntry = name;
            if (mcaType == McaType.CHUNK) {
                if ("xPos".equals(name) || "zPos".equals(name)) {
                    return EntryResult.ENTER;
                }
                if ("Level".equals(name) && type == CompoundTag.TYPE) {
                    depth++;
                    return EntryResult.ENTER;
                }
                return EntryResult.SKIP;
            } else if (mcaType == McaType.ENTITIES) {
                if ("Position".equals(name)) {
                    if (type == CompoundTag.TYPE) {
                        depth++;
                    }
                    return EntryResult.ENTER;
                }
                if (depth > 0 && ("x".equals(name) || "z".equals(name))) {
                    return EntryResult.ENTER;
                }
                return EntryResult.SKIP;
            }
            return EntryResult.SKIP;
        }

        @Override
        public ValueResult visit(int value) {
            if ("xPos".equals(currentEntry) || (depth > 0 && "x".equals(currentEntry))) {
                foundX = value;
            } else if ("zPos".equals(currentEntry) || (depth > 0 && "z".equals(currentEntry))) {
                foundZ = value;
            }
            if (foundX != null && foundZ != null) {
                return ValueResult.HALT;
            }
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(int[] value) {
            if ("Position".equals(currentEntry) && value != null && value.length >= 2) {
                foundX = value[0];
                foundZ = value[1];
                return ValueResult.HALT;
            }
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(long value) {
            if ("Position".equals(currentEntry)) {
                foundX = ChunkPos.getX(value);
                foundZ = ChunkPos.getZ(value);
                return ValueResult.HALT;
            }
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visitContainerEnd() {
            if (depth > 0) {
                depth--;
            }
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visitEnd() {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(String value) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(byte value) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(short value) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(float value) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(double value) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(byte[] value) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visit(long[] value) {
            return ValueResult.CONTINUE;
        }

        @Override
        public ValueResult visitList(TagType<?> type, int size) {
            return ValueResult.CONTINUE;
        }

        @Override
        public EntryResult visitElement(TagType<?> type, int index) {
            return EntryResult.SKIP;
        }
    }
}
