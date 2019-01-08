package org.labkey.meetingtools;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.data.ContainerManager;
import org.labkey.api.security.SecurityManager;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.Pair;

import javax.servlet.http.HttpSession;
import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.HandshakeResponse;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerEndpointConfig;
import java.io.IOException;
import java.util.Set;

public abstract class AbstractWebsocketServer
{
    protected MeetingToolsManager.AbstractMeetingToolsSubManager _manager;

    public static class Configurator extends ServerEndpointConfig.Configurator
    {

        @Override
        public void modifyHandshake(ServerEndpointConfig config, HandshakeRequest request, HandshakeResponse response)
        {
            HttpSession session = (HttpSession) request.getHttpSession();
            User user = SecurityManager.getSessionUser(session);

            // config.getUserProperties() is backed by a ConcurrentHashMap which does not allow null keys or values.
            if (session != null)
                config.getUserProperties().put("httpSession", session);

            config.getUserProperties().put("userId", null == user ? 0 : user.getUserId());
        }
    }

    @OnOpen
    public abstract void onOpen(Session session, EndpointConfig config) throws IOException;

    @OnClose
    public abstract void onClose(Session session, CloseReason reason);

    @OnMessage
    public void onMessage(Session session, String msg)
    {
        JSONObject inJson = new JSONObject(msg);
        JSONObject outJson = new JSONObject();

        User user = UserManager.getUser((int) session.getUserProperties().get("userId"));
        String type = inJson.getString("type");
        Container container;
        Pair<Container, String> room;

        if(inJson.optString("container") != "")
            container = ContainerManager.getForPath(inJson.optString("container"));
        else
            container = _manager.getRoomForSession(user, session).getKey();

        if(inJson.optString("roomName") != "")
            room = new Pair<>(container, inJson.getString("roomName"));
        else
            room = _manager.getRoomForSession(user, session);

        MessageMeta meta;

        meta = new MessageMeta(session, user, container, type, room);

        processMessage(inJson, outJson, meta);

        if(meta.getRoom() == null)
            meta.setRoom(_manager.getRoomForSession(user, session));

        sendMessage(outJson, meta);
    }

    protected abstract void processMessage(JSONObject inJson, JSONObject outJson, MessageMeta meta);

    protected void sendMessage(JSONObject outJson, MessageMeta meta)
    {
        Set<Session> recipients = meta.isSendMessageToAllContainerUsers() ? _manager.getSessionsForContainer(meta.getContainer()) : _manager.getSessionsForRoom(meta.getRoom());

        for (Session rcp : recipients)
        {
            if (meta.isSendMessage() && (!rcp.equals(meta.getSession()) || meta.isSendMessageToSender()))
            {
                try
                {
                    rcp.getBasicRemote().sendText(outJson.toString());
                }
                catch (IOException e)
                {
                    onMessage(rcp, "{type: 'disconn'}");
                }
            }
        }
    }

    protected boolean sendSingleMessage(Session recipient, JSONObject outJson)
    {
        try
        {
            recipient.getBasicRemote().sendText(outJson.toString());
            return true;
        }
        catch(IOException e)
        {
            onMessage(recipient, "{type: 'disconn'}");
            return false;
        }
    }

    protected class MessageMeta
    {
        /**
         * A simple structure class to contain message metadata for passing to processMessage.
         * More than anything, it exists to make processMessage take a reasonable number of arguments.
         */

        private Session _session;
        private String _msgType;
        private Container _container;
        private User _user;
        private Pair<Container, String> _room;

        private boolean _sendMessage = true;
        private boolean _sendMessageToSender = true;
        private boolean _sendMessageToAllContainerUsers = false;

        public MessageMeta()
        {
            //Allow empty construction
        }

        public MessageMeta(Session session, User user, Container container, String msgType)
        {
            this(session, user, container, msgType, null);
        }

        public MessageMeta(Session session, User user, Container container, String msgType, @Nullable Pair<Container, String> room)
        {
            _session = session;
            _user = user;
            _container = container;
            _msgType = msgType;
            _room = room;
        }

        public void setSession(Session session)
        {
            _session = session;
        }

        public Session getSession()
        {
            return _session;
        }

        public void setMsgType(String msgType)
        {
            _msgType = msgType;
        }

        public String getMsgType()
        {
            return _msgType;
        }

        public void setContainer(Container container)
        {
            _container = container;
        }

        public Container getContainer()
        {
            return _container;
        }

        public void setUser(User user)
        {
            _user = user;
        }

        public User getUser()
        {
            return _user;
        }

        public void setRoom(Pair<Container, String> room)
        {
            _room = room;
        }

        public Pair<Container, String> getRoom()
        {
            return _room;
        }

        public void setSendMessage(boolean sendMessage)
        {
            _sendMessage = sendMessage;
        }

        public boolean isSendMessage()
        {
            return _sendMessage;
        }

        public void setSendMessageToSender(boolean sendMessageToSender)
        {
            _sendMessageToSender = sendMessageToSender;
        }

        public boolean isSendMessageToSender()
        {
            return _sendMessageToSender;
        }

        public void setSendMessageToAllContainerUsers(boolean sendMessageToAllContainerUsers)
        {
            _sendMessageToAllContainerUsers = sendMessageToAllContainerUsers;
        }

        public boolean isSendMessageToAllContainerUsers()
        {
            return _sendMessageToAllContainerUsers;
        }
    }
}
