## Qilletni Release Protocol

Qilletni consists of several repositories, so releasing in the correct order is critical.

### Artifact Structure

QilletniToolchain and QPMCLI individually contain executable artifacts, produced by their own CI/CD pipelines automatically. When a normal commit is pushed and the Gradle version is a `*-SNAPSHOT` version, the `snapshot` tag is moved to the latest commit, and a release is created with the updated binaries. When a version tag (e.g. `v2.4.3`) is pushed, if the Gradle version matches (without the leading `v`), a new, non-preview release is created, with the final artifacts.

#### Docker Publication

Docker publication occurs manually, on the main Qilletni repository. This may be triggered for a **snapshot** or **release** type. If a snapshot container is created, the latest **snapshot** releases from both the QilletniToolchain and QPMCLI are grabbed, and a docker image is created from both of them.

The snapshot docker image tags are `snapshot` and a combination of both the QilletniToolchain and QPMCLI latest snapshot commit's SHA's. For example, if the last snapshot commit in QilletniToolchain is `4d922f2` and QPMCLI's is `fd85e50`, the tag given to the image is `4d922f2-fd85e50`.

When triggering a release, a version is given as an input, and the QilletniToolchain and QPMCLI versions from the local Dockerfile are used.

### Maven Publishing

For Qilletni repositories that are published via Maven Central, artifacts are built and uploaded to the Sonatype's snapshot repository on every commit that has a Gradle version ending in `-SNAPSHOT`.

When a version tag (e.g. `v1.4.2`) is pushed, and the Gradle version matches (without the leading `v`).
