package org.labkey.meetingtools;

import org.json.JSONObject;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.Pair;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.OnOpen;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.Session;

import java.io.IOException;

/**
 * Endpoint for Chat websocket connections
 */

@ServerEndpoint(value="/ChatServer", configurator=ChatWebsocketServer.Configurator.class)
public class ChatWebsocketServer extends AbstractWebsocketServer
{
    protected ChatWebsocketServer()
    {
        _manager = MeetingToolsManager.getChat();
    }

    @Override
    @OnOpen
    public void onOpen(Session session, EndpointConfig config) throws IOException
    {
        //Connections handled with init message;
    }

    @Override
    @OnClose
    public void onClose(Session session, CloseReason reason)
    {
        //Disconnections handled with disconn message.
        //If that fails...try here?
        if(MeetingToolsManager.getChat().isSessionConnected(UserManager.getUser((int)session.getUserProperties().get("userId")), session))
            onMessage(session, "{type:'disconn'}");
    }

    public void processMessage(JSONObject inJson, JSONObject outJson, MessageMeta meta)
    {
        if(meta.getUser() != null)
        {
            switch (meta.getMsgType())
            {
                case "msg":
                    if (inJson.optString("text") != null && !inJson.optString("text").trim().equals(""))
                    {
                        outJson.put("msgType", "msg");
                        outJson.put("username", meta.getUser().getDisplayName(null));
                        outJson.put("message", inJson.optString("text"));
                    }
                    else
                        return;
                    break;
                case "init":
                    if (!MeetingToolsManager.getChat().isSessionConnected(meta.getUser(), meta.getSession()) || inJson.optBoolean("isRoomSwitch"))
                    {
                        boolean isNewUserToRoom = MeetingToolsManager.getChat().isNewUserToRoom(meta.getUser(), new Pair<>(meta.getContainer(), inJson.optString("roomName")));
                        MeetingToolsManager.getChat().addConnection(meta.getUser(), meta.getSession(), meta.getContainer(), inJson.optString("roomName"));
                        if(isNewUserToRoom)
                        {
                            outJson.put("msgType", "init");
                            outJson.put("activeRoom", inJson.optString("roomName"));
                            outJson.put("message", "Connected to chat room " + inJson.optString("roomName") + " in folder " + inJson.optString("container"));
                            outJson.put("users", String.join(",", MeetingToolsManager.getChat().getUsernamesForRoom(meta.getContainer(), inJson.optString("roomName"))));
                            outJson.put("rooms", String.join(",", MeetingToolsManager.getChat().getRoomNamesForContainer(meta.getContainer())));

                            if(!sendSingleMessage(meta.getSession(), outJson))
                                return;

                            meta.setSendMessageToSender(false);
                            outJson.clear();
                            outJson.put("username", meta.getUser().getDisplayName(null));
                            outJson.put("msgType", "newconn");
                            outJson.put("message", meta.getUser().getDisplayName(null) + " has joined!");
                        }
                        else
                        {
                            meta.setSendMessage(false);
                            outJson.put("msgType", "init");
                            outJson.put("activeRoom", inJson.optString("roomName"));
                            outJson.put("message", "Connected to chat room " + inJson.optString("roomName") + " in folder " + inJson.optString("container"));
                            outJson.put("users", String.join(",", MeetingToolsManager.getChat().getUsernamesForRoom(meta.getContainer(), inJson.optString("roomName"))));
                            outJson.put("rooms", String.join(",", MeetingToolsManager.getChat().getRoomNamesForContainer(meta.getContainer())));

                            if(!sendSingleMessage(meta.getSession(), outJson))
                                return;
                        }
                    }
                    else
                    {
                        meta.setSendMessage(false);
                        outJson.put("msgType", "server");
                        outJson.put("message", "Connected to chat room '" + inJson.optString("roomName") + "' in folder " + inJson.optString("container"));

                        if(!sendSingleMessage(meta.getSession(), outJson))
                            return;
                    }
                    break;
                case "switchroom":
                    if(meta.getContainer() != null && inJson.optString("roomName") != null)
                    {
                        JSONObject srJson = new JSONObject();
                        srJson.put("isRoomSwitch", true);
                        srJson.put("type", "disconn");
                        srJson.put("container", inJson.optString("container"));
                        srJson.put("roomName", inJson.optString("roomName"));
                        onMessage(meta.getSession(), srJson.toString());
                        srJson.put("type", "init");
                        onMessage(meta.getSession(), srJson.toString());
                    }
                    meta.setSendMessage(false);
                    break;
                case "newroom":
                    if(meta.getContainer() != null)
                    {
                        if(MeetingToolsManager.getChat().getRoomNamesForContainer(meta.getContainer()).contains(inJson.optString("roomName")))
                        {
                            outJson.put("msgType", "error");
                            outJson.put("message", "A chat room with the name '" + inJson.optString("roomName") + "' already exists. Please try again with a unique name.");

                            sendSingleMessage(meta.getSession(), outJson);
                            return;
                        }
                        outJson.put("msgType", "newroom");
                        outJson.put("roomName", inJson.optString("roomName"));
                        outJson.put("username", meta.getUser().getDisplayName(null));
                        meta.setSendMessageToAllContainerUsers(true);
                    }
                    break;
                case "delroom":
                    if(meta.getContainer() != null)
                    {
                        JSONObject srJson = new JSONObject();
                        srJson.put("container", meta.getContainer().getPath());
                        srJson.put("roomName", "General");
                        for(Session mover : MeetingToolsManager.getChat().getSessionsForRoom(new Pair<>(meta.getContainer(), inJson.optString("roomName"))))
                        {
                            onMessage(mover, srJson.toString());
                        }
                        outJson.put("msgType", "delroom");
                        outJson.put("roomName", inJson.optString("roomName"));
                        meta.setSendMessageToAllContainerUsers(true);
                    }
                    break;
                case "disconn":
                    meta.setRoom(MeetingToolsManager.getChat().getRoomForSession(meta.getUser(), meta.getSession())); //Get room before removing or switching connection
                    MeetingToolsManager.getChat().removeConnection(meta.getUser(), meta.getSession()); //Disconnect
                    if(MeetingToolsManager.getChat().shouldDeleteRoom(meta.getRoom())) //If no one is left in the room, delete it from clients
                    {
                        JSONObject dJson = new JSONObject();
                        dJson.put("type", "delroom");
                        dJson.put("container", meta.getRoom().getKey().getPath());
                        dJson.put("roomName", meta.getRoom().getValue());
                        onMessage(meta.getSession(), dJson.toString());
                        if(inJson.optBoolean("isRoomSwitch"))
                        {
                            try
                            {
                                dJson.put("msgType", "delroom");
                                meta.getSession().getBasicRemote().sendText(dJson.toString());
                            }
                            catch(IOException ignored){} //We're already disconnected, so what can we do? It'll have to be cleaned up later.
                        }
                    }
                    if(!MeetingToolsManager.getChat().getUsersForRoom(meta.getRoom()).contains(meta.getUser()))
                    {
                        outJson.put("username", meta.getUser().getDisplayName(null));
                        outJson.put("msgType", "disconn");
                        outJson.put("message", meta.getUser().getDisplayName(null) + " has left.");
                    }
                    break;
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error)
    {

    }


}