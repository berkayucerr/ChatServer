package com.example.chatserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.kurento.client.IceCandidate;
import org.kurento.client.KurentoClient;
import org.kurento.client.MediaPipeline;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class RoomManager {
    private final Map<String, Room> rooms = new ConcurrentHashMap<>();
    private final KurentoClient kurentoClient;
    private final ObjectMapper mapper = new ObjectMapper();
    private final Map<String, WebSocketSession> userSessions = new ConcurrentHashMap<>();

    public RoomManager(KurentoClient kurentoClient) {
        this.kurentoClient = kurentoClient;
    }

    public Room getOrCreateRoom(String channelId) {
        return rooms.computeIfAbsent(channelId, id -> {
            MediaPipeline pipeline = kurentoClient.createMediaPipeline();
            return new Room(id, pipeline);
        });
    }

    public WebRtcEndpoint joinRoom(String channelId, String userId, WebSocketSession session) {
        System.out.println("Odaya katılma isteği - channelId: " + channelId + ", userId: " + userId);

        Room room = getOrCreateRoom(channelId);
        WebRtcEndpoint endpoint = room.getParticipantEndpoint(userId);


        room.getParticipants().forEach((participantId, participantEndpoint) -> {
            if (!participantId.equals(userId)) {
                try {
                    endpoint.connect(participantEndpoint);
                    participantEndpoint.connect(endpoint);

                    System.out.println("Medya bağlantısı kuruldu: " + userId + " <-> " + participantId);
                } catch (Exception e) {
                    System.err.println("Medya bağlantısı hatası: " + e.getMessage());
                }
            }
        });

        endpoint.addIceCandidateFoundListener(event -> {
            try {
                Map<String, Object> response = new HashMap<>();
                response.put("type", "iceCandidate");
                response.put("candidate", new HashMap<String, Object>() {{
                    put("candidate", event.getCandidate().getCandidate());
                    put("sdpMid", event.getCandidate().getSdpMid());
                    put("sdpMLineIndex", event.getCandidate().getSdpMLineIndex());
                }});

                session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
            } catch (Exception e) {
                System.err.println("ICE Candidate gönderme hatası: " + e.getMessage());
            }
        });

        endpoint.addMediaFlowInStateChangeListener
                (event -> System.out.println("MediaFlowIn değişti - userId: "
                        + userId
                        + ", state: "
                        + event.getState()));

        endpoint.addMediaFlowOutStateChangeListener(event -> System.out.println("MediaFlowOut değişti - userId: "
                + userId
                + ", state: "
                + event.getState()));

        endpoint.gatherCandidates();
        return endpoint;
    }

    public void leaveRoom(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.removeParticipant(userId);
            userSessions.remove(userId);


            broadcastUserLeft(roomId, userId);

            if (room.getParticipants().isEmpty()) {
                room.getPipeline().release();
                rooms.remove(roomId);
            }
        }
    }

    public void handleIceCandidate(String roomId, String userId, IceCandidate candidate) {
        Room room = rooms.get(roomId);
        if (room != null) {
            WebRtcEndpoint endpoint = room.getParticipantEndpoint(userId);
            if (endpoint != null) {
                endpoint.addIceCandidate(candidate);
            }
        }
    }

    public void processOffer(String roomId, String userId, String sdpOffer) throws IOException {
        Room room = rooms.get(roomId);
        if (room != null) {
            WebRtcEndpoint endpoint = room.getParticipantEndpoint(userId);
            if (endpoint != null) {
                String sdpAnswer = endpoint.processOffer(sdpOffer);
                endpoint.gatherCandidates();

                WebSocketSession session = userSessions.get(userId);
                if (session != null) {
                    Map<String, Object> response = Map.of(
                            "type", "sdpAnswer",
                            "sdpAnswer", sdpAnswer
                    );
                    session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
                }
            }
        }
    }

    private void broadcastUserJoined(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            Map<String, Object> message = Map.of(
                    "type", "userJoined",
                    "userId", userId
            );
            broadcastToRoom(roomId, message, userId);
        }
    }

    private void broadcastUserLeft(String roomId, String userId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            Map<String, Object> message = Map.of(
                    "type", "userLeft",
                    "userId", userId
            );
            broadcastToRoom(roomId, message, userId);
        }
    }

    private void broadcastToRoom(String roomId, Map<String, Object> message, String excludeUserId) {
        Room room = rooms.get(roomId);
        if (room != null) {
            room.getParticipants().keySet().stream()
                    .filter(participantId -> !participantId.equals(excludeUserId))
                    .map(userSessions::get)
                    .filter(Objects::nonNull)
                    .forEach(session -> {
                        try {
                            session.sendMessage(new TextMessage(mapper.writeValueAsString(message)));
                        } catch (IOException e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    public WebRtcEndpoint getParticipantEndpoint(String roomId, String userId) {
        Room room = rooms.get(roomId);
        return room != null ? room.getParticipantEndpoint(userId) : null;
    }

    public void disconnectUser(String userId) {
        rooms.values().forEach(room -> {
            if (room.getParticipants().containsKey(userId)) {
                leaveRoom(room.getRoomId(), userId);
            }
        });
    }
}