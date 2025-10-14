package com.example.reloader.stage;

public record StageStatus(
        String site,
        int senderId,
        long total,
        long ready,
        long enqueued,
        long failed,
        long completed
) {}
