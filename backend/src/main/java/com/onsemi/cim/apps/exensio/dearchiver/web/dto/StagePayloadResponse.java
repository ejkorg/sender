package com.onsemi.cim.apps.exensio.dearchiver.web.dto;

import java.util.List;

public record StagePayloadResponse(int staged,
								   int duplicates,
								   List<DuplicatePayloadView> duplicatePayloads,
								   int dispatched,
								   boolean requiresConfirmation) {}
