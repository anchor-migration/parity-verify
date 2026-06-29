package com.anchor.migration.parityverify.model;

import java.util.Map;

public record ParityChange(
        EntityKind entityKind,
        ChangeKind changeKind,
        String stableId,
        Map<String, String> before,
        Map<String, String> after,
        String note) {}
