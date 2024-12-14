package com.example.chatserver;

import lombok.Getter;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
@Getter
public class Room {
    private final String roomId;
    private final MediaPipeline pipeline;
    private final Map<String, WebRtcEndpoint> participants = new ConcurrentHashMap<>();

    public Room(String roomId, MediaPipeline pipeline) {
        this.roomId = roomId;
        this.pipeline = pipeline;
    }

    public WebRtcEndpoint getParticipantEndpoint(String userId) {
        return participants.computeIfAbsent(userId, id -> {
            System.out.println("Yeni endpoint oluşturuluyor - userId: " + id);
            return new WebRtcEndpoint.Builder(pipeline).build();
        });
    }

    public void addParticipant(String userId, WebRtcEndpoint endpoint) {
        System.out.println("Katılımcı ekleniyor - userId: " + userId);
        participants.put(userId, endpoint);
    }


    public void removeParticipant(String userId) {
        WebRtcEndpoint endpoint = participants.remove(userId);
        if (endpoint != null) {
            endpoint.release();
        }
    }

    public Map<String, WebRtcEndpoint> getParticipants() {
        return participants;
    }

    public MediaPipeline getPipeline() {
        return pipeline;
    }
}