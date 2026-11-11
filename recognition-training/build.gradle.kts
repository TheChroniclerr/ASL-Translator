plugins { application }

java {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
}

application { mainClass = "com.group_nine.asl_translator.training.TrainingTool" }

tasks.named<JavaExec>("run") {
    workingDir = rootProject.projectDir
    maxHeapSize = "2g"
}

dependencies {
    implementation(project(":recognition-core"))
    implementation("org.tribuo:tribuo-classification-tree:4.3.2")
    implementation("org.json:json:20240303")
    testImplementation("junit:junit:4.13.2")
}
