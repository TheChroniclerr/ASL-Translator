package com.group_nine.asl_translator.training;

import java.awt.image.BufferedImage;
import java.awt.Graphics2D;
import java.io.*;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.zip.*;
import javax.imageio.ImageIO;

/** Prepares the pinned public archive; no data is fetched during ordinary app builds. */
final class DatasetPreparer {
    static final String ARCHIVE_SHA256 = "1fb90e83a9d4d3548545e487b5c7b7be8ff6e6929a8751b79c447501d7bd408f";
    private static final class Image {
        String id, label, hash, group, split;
        long perceptualHash;
        int parent;
    }

    static String sha(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            return hex(digest);
        } catch (Exception e) { throw new IllegalStateException(e); }
    }

    static String fileSha(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream input = Files.newInputStream(path)) {
            byte[] buffer = new byte[65536];
            int n;
            while ((n = input.read(buffer)) >= 0) digest.update(buffer, 0, n);
        }
        return hex(digest.digest());
    }

    private static String hex(byte[] bytes) {
        char[] digits = "0123456789abcdef".toCharArray(), output = new char[bytes.length * 2];
        for (int i = 0; i < bytes.length; i++) {
            output[i * 2] = digits[(bytes[i] & 255) >>> 4]; output[i * 2 + 1] = digits[bytes[i] & 15];
        }
        return new String(output);
    }

    static void prepare(Path archive, Path destination) throws Exception {
        if (!fileSha(archive).equals(ARCHIVE_SHA256)) throw new IOException("SignAlphaSet archive checksum mismatch");
        Files.createDirectories(destination.resolve("images"));
        Map<String, List<Image>> byLetter = new TreeMap<>();
        Map<String, String> seenLabels = new HashMap<>();
        Set<String> conflicts = new HashSet<>();
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            List<? extends ZipEntry> entries = Collections.list(zip.entries());
            entries.sort(Comparator.comparing(ZipEntry::getName));
            for (ZipEntry entry : entries) {
                String[] parts = entry.getName().split("/");
                if (entry.isDirectory() || parts.length != 3 || !parts[0].equals("SignAlphaSet")
                        || !parts[1].matches("[A-Z]") || parts[1].equals("J") || parts[1].equals("Z")
                        || !parts[2].matches("[A-Z]_[0-9]+\\.jpg")) continue;
                byte[] bytes;
                try (InputStream input = zip.getInputStream(entry)) { bytes = input.readAllBytes(); }
                BufferedImage decoded = ImageIO.read(new ByteArrayInputStream(bytes));
                if (decoded == null) continue;
                Image image = new Image();
                image.id = parts[2]; image.label = parts[1]; image.hash = sha(bytes);
                image.perceptualHash = differenceHash(decoded);
                String previous = seenLabels.putIfAbsent(image.hash, image.label);
                if (previous != null && !previous.equals(image.label)) conflicts.add(image.hash);
                byLetter.computeIfAbsent(image.label, ignored -> new ArrayList<>()).add(image);
                Files.write(destination.resolve("images").resolve(image.id), bytes);
            }
        }
        int groups = 0, images = 0, exclusions = 0;
        try (PrintWriter manifest = new PrintWriter(Files.newBufferedWriter(destination.resolve("manifest.tsv")))) {
            manifest.println("id\tletter\tgroup\tsplit\tsha256\tdhash");
            for (List<Image> list : byLetter.values()) {
                exclusions += (int) list.stream().filter(i -> conflicts.contains(i.hash)).count();
                list.removeIf(i -> conflicts.contains(i.hash));
                for (int i = 0; i < list.size(); i++) list.get(i).parent = i;
                for (int i = 0; i < list.size(); i++) {
                    for (int j = 0; j < i; j++) {
                        if (list.get(i).hash.equals(list.get(j).hash)
                                || Long.bitCount(list.get(i).perceptualHash ^ list.get(j).perceptualHash) <= 2) {
                            list.get(root(list, i)).parent = root(list, j);
                        }
                    }
                }
                Map<Integer, List<Image>> clusters = new LinkedHashMap<>();
                for (int i = 0; i < list.size(); i++) {
                    clusters.computeIfAbsent(root(list, i), ignored -> new ArrayList<>()).add(list.get(i));
                }
                List<List<Image>> ordered = new ArrayList<>(clusters.values());
                ordered.sort(Comparator.comparing(c -> sha(("470:" + c.get(0).id).getBytes(java.nio.charset.StandardCharsets.UTF_8))));
                if (ordered.size() < 3) throw new IOException("Too few independent image groups for " + list.get(0).label);
                int used = 0;
                for (int g = 0; g < ordered.size(); g++) {
                    List<Image> cluster = ordered.get(g);
                    String split = used < list.size() * .70 ? "train" : used < list.size() * .85 ? "validation" : "test";
                    // Guarantee each letter has all three splits even with uneven groups.
                    if (g == ordered.size() - 2 && used < list.size() * .70) split = "validation";
                    if (g == ordered.size() - 1) split = "test";
                    String group = cluster.get(0).label + "-" + cluster.get(0).hash.substring(0, 16);
                    for (Image image : cluster) {
                        image.group = group; image.split = split;
                        manifest.println(image.id + "\t" + image.label + "\t" + group + "\t" + split
                                + "\t" + image.hash + "\t" + Long.toUnsignedString(image.perceptualHash, 16));
                    }
                    used += cluster.size(); groups++;
                }
                images += list.size();
                System.out.println(list.get(0).label + ": " + list.size() + " images, " + clusters.size() + " duplicate groups");
            }
        }
        Files.writeString(destination.resolve("preparation.txt"), "SignAlphaSet v2, DOI 10.17632/8fmvr9m98w.2\n"
                + "archive_sha256=" + ARCHIVE_SHA256 + "\nimages=" + images + "\ngroups=" + groups
                + "\nconflicting_label_images_excluded=" + exclusions
                + "\nExcluded J/Z (movement), dynamic archive, unreadable images.\n"
                + "Grouping: exact SHA-256 and connected dHash distance <=2 within each letter.\n"
                + "Signer/session identifiers are not supplied; split is NOT signer-independent.\n");
    }

    private static int root(List<Image> images, int i) {
        while (images.get(i).parent != i) {
            images.get(i).parent = images.get(images.get(i).parent).parent;
            i = images.get(i).parent;
        }
        return i;
    }

    private static long differenceHash(BufferedImage input) {
        BufferedImage small = new BufferedImage(9, 8, BufferedImage.TYPE_BYTE_GRAY);
        Graphics2D graphics = small.createGraphics();
        graphics.drawImage(input, 0, 0, 9, 8, null); graphics.dispose();
        long hash = 0;
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) {
            hash = (hash << 1) | (small.getRaster().getSample(x, y, 0) > small.getRaster().getSample(x + 1, y, 0) ? 1 : 0);
        }
        return hash;
    }
}
