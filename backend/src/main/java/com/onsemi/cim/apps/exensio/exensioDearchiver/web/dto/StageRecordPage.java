package com.onsemi.cim.apps.exensio.exensioDearchiver.web.dto;

import java.util.List;

public record StageRecordPage(List<StageRecordView> items, long total, int page, int size) {}
