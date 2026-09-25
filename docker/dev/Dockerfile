FROM maven:3.9.16-eclipse-temurin-21-noble@sha256:a972570be789ee5c9fa23446a8914ac7327560b5c022f662cfa9452aef829f18

ARG TARGETARCH

# Keep the Java binding and native libraries from the same Z3 release.
RUN set -eu; \
    case "$TARGETARCH" in \
      arm64) archive=z3-4.13.3-arm64-glibc-2.34; \
        checksum=5bc5fd2c501445970de3b13d5699ff601ff51d3e496cb15ce67d7ee2a4a93b03 ;; \
      amd64) archive=z3-4.13.3-x64-glibc-2.35; \
        checksum=32c7377026733c9d7b33c21cd77a68f50ba682367207b031a6bfd80140a8722f ;; \
      *) echo "Unsupported architecture: $TARGETARCH" >&2; exit 1 ;; \
    esac; \
    curl --fail --location --retry 3 --output /tmp/z3.zip \
      "https://github.com/Z3Prover/z3/releases/download/z3-4.13.3/$archive.zip"; \
    printf '%s  /tmp/z3.zip\n' "$checksum" | sha256sum --check --strict; \
    mkdir /tmp/z3-extract; \
    cd /tmp/z3-extract; \
    jar --extract --file /tmp/z3.zip; \
    mv "$archive" /opt/z3; \
    rm -rf /tmp/z3.zip /tmp/z3-extract

ENV LD_LIBRARY_PATH=/opt/z3/bin
ENV PATH=/opt/z3/bin:${PATH}

# The official Maven entrypoint seeds new caches from this reference repository.
RUN mvn --batch-mode --no-transfer-progress \
      org.apache.maven.plugins:maven-install-plugin:3.1.2:install-file \
      -Dmaven.repo.local=/usr/share/maven/ref/repository \
      -Dfile=/opt/z3/bin/com.microsoft.z3.jar \
      -DgroupId=com.microsoft -DartifactId=z3 -Dversion=4.13.3 \
      -Dpackaging=jar -DgeneratePom=true

COPY Z3Smoke.java /opt/maze/Z3Smoke.java

# Exercise JNI and solving during the image build, not just the Z3 executable.
RUN java -cp /opt/z3/bin/com.microsoft.z3.jar /opt/maze/Z3Smoke.java

COPY --chmod=755 entrypoint.sh /usr/local/bin/maze-dev-entrypoint

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/maze-dev-entrypoint"]
CMD ["mvn", "--batch-mode", "--no-transfer-progress", "verify"]
