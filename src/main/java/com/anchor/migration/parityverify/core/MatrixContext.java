package com.anchor.migration.parityverify.core;

import com.anchor.migration.parityverify.model.ParityReport;

import java.nio.file.Path;
import java.util.Optional;

public record MatrixContext(
        Path beforeDb,
        Path afterDb,
        Path linkedBeforeDb,
        Path linkedAfterDb,
        ParityReport parityReport,
        Optional<Path> touchpointSourceFile) {}
