package com.group_nine.asl_translator.training;

import java.io.IOException;
import java.nio.file.*;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

/** Prevents accidentally reporting leaked or mislabeled data as held-out evaluation. */
public class TrainingInputTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private String row(String id, String letter, String group, String split) {
        StringBuilder line = new StringBuilder(id + "," + letter + "," + group + "," + split + ",296,296");
        for (int i = 0; i < 21; i++) line.append(",").append(.4 + i * .006)
                .append(",").append(.8 - i * .02).append(",").append(-i * .002);
        return line + "\n";
    }

    private void train(String rows) throws Exception {
        Path csv = temporary.newFile("landmarks.csv").toPath();
        StringBuilder header = new StringBuilder("id,letter,group,split,width,height");
        for (int i = 0; i < 63; i++) header.append(",p").append(i);
        Files.writeString(csv, header + "\n" + rows);
        TrainingTool.main(new String[]{"train", csv.toString(), temporary.newFolder("output").getPath()});
    }

    @Test(expected = IOException.class) public void rejectsGroupsSharedAcrossTrainingAndTest() throws Exception {
        train(row("A_0.jpg", "A", "same-group", "train") + row("A_1.jpg", "A", "same-group", "test"));
    }

    @Test(expected = IOException.class) public void rejectsRepeatedImages() throws Exception {
        train(row("A_0.jpg", "A", "one", "train") + row("A_0.jpg", "A", "two", "train"));
    }

    @Test(expected = IOException.class) public void rejectsMovementLetters() throws Exception {
        train(row("J_0.jpg", "J", "one", "train"));
    }

    @Test(expected = IOException.class) public void refusesAnUnverifiedPublicArchive() throws Exception {
        Path archive = temporary.newFile("dataset.zip").toPath();
        DatasetPreparer.prepare(archive, temporary.getRoot().toPath().resolve("prepared"));
    }
}
