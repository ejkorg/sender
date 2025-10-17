package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

import java.util.List;

public record StagePayloadResponse(int staged,
								   int duplicates,
								   List<DuplicatePayloadView> duplicatePayloads,
								   int dispatched,
								   boolean requiresConfirmation) {}
