package com.example.reloader.stage;

import java.util.List;

public record StageStatus(
                String site,
                int senderId,
                long total,
                long ready,
                long enqueued,
                long failed,
                long completed,
                List<StageUserStatus> users
) {
        public StageStatus {
                users = users == null ? List.of() : List.copyOf(users);
        }
}
