package com.anchor.migration.parityverify.model;

import java.util.List;

public record ParityReport(
        String toolVersion,
        String beforeDb,
        String afterDb,
        String beforeExportRunId,
        String afterExportRunId,
        ParitySummary summary,
        List<ParityChange> changes) {}
