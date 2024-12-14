package com.example.chatserver;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.kurento.client.IceCandidate;
import org.kurento.client.WebRtcEndpoint;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.HashMap;
import java.util.Map;
@Slf4j
public class RoomWebSocketHandler extends TextWebSocketHandler {

    private final RoomManager roomManager;
    private final ObjectMapper mapper = new ObjectMapper();

    public RoomWebSocketHandler(RoomManager roomManager) {
        this.roomManager = roomManager;
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        String payload = message.getPayload();
        Map<String, Object> msg = mapper.readValue(payload, Map.class);
        System.out.println("Gelen mesaj: " + payload);

        String action = msg.get("action").toString();
        String channelId = msg.get("channelId").toString();
        String userId = session.getId();

        switch (action) {
            case "joinVoiceChannel" -> handleJoinVoiceChannel(session, channelId, userId);
            case "webrtcOffer" -> handleWebRTCOffer(session, channelId, userId, msg);
            case "iceCandidate" -> handleICECandidate(session, channelId, msg);
            case "leave" -> handleLeave(channelId, userId);
        }
    }

    private void handleJoinVoiceChannel(WebSocketSession session, String channelId, String userId) throws Exception {
        try {
            WebRtcEndpoint endpoint = roomManager.joinRoom(channelId, userId, session);
            Map<String, Object> response = Map.of(
                    "type", "joinResponse",
                    "status", "success"
            );
            session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));

        } catch (Exception e) {
            Map<String, Object> errorResponse = Map.of(
                    "type", "error",
                    "message", "Kanala katılırken hata: " + e.getMessage()
            );
            session.sendMessage(new TextMessage(mapper.writeValueAsString(errorResponse)));
        }
    }

    private void handleWebRTCOffer(WebSocketSession session, String channelId, String userId, Map<String, Object> msg) throws Exception {
        try {
            Room room = roomManager.getOrCreateRoom(channelId);
            WebRtcEndpoint endpoint = room.getParticipantEndpoint(userId);

            if (endpoint != null) {

                Map<String, Object> offerMap = (Map<String, Object>) msg.get("offer");
                String sdp = offerMap.get("sdp").toString();

                log.info("İşlenecek SDP: " + sdp);

                String answer = endpoint.processOffer(sdp);
                endpoint.gatherCandidates();
                Map<String, Object> response = new HashMap<>();
                response.put("type", "webrtcAnswer");
                response.put("answer", new HashMap<String, Object>() {{
                    put("type", "answer");
                    put("sdp", answer);
                }});

                session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
                System.out.println("SDP Answer gönderildi: " + answer); // Debug için
            } else {
                System.err.println("Endpoint bulunamadı - userId: " + userId);
                throw new Exception("Endpoint not found for user");
            }
        } catch (Exception e) {
            System.err.println("WebRTC Offer işleme hatası: " + e.getMessage());
            e.printStackTrace();

            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("type", "error");
            errorResponse.put("message", "WebRTC Offer işlenirken hata: " + e.getMessage());
            session.sendMessage(new TextMessage(mapper.writeValueAsString(errorResponse)));
        }
    }

    private void handleICECandidate(WebSocketSession session, String channelId, Map<String, Object> msg) throws Exception {
        Room room = roomManager.getOrCreateRoom(channelId);
        WebRtcEndpoint endpoint = room.getParticipantEndpoint(session.getId());

        if (endpoint != null) {
            Map<String, Object> candidateMap = (Map<String, Object>) msg.get("candidate");
            IceCandidate candidate = new IceCandidate(
                    candidateMap.get("candidate").toString(),
                    candidateMap.get("sdpMid").toString(),
                    ((Number) candidateMap.get("sdpMLineIndex")).intValue()
            );
            endpoint.addIceCandidate(candidate);
            System.out.println("ICE aday başarıyla eklendi: " + candidate.getCandidate());
            Map<String, Object> response = Map.of(
                    "type", "iceCandidateResponse",
                    "status", "success"
            );
            session.sendMessage(new TextMessage(mapper.writeValueAsString(response)));
        }
    }

    private void handleLeave(String channelId, String userId) {
        roomManager.leaveRoom(channelId, userId);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        String userId = session.getId();
        roomManager.disconnectUser(userId);
        super.afterConnectionClosed(session, status);
    }
}