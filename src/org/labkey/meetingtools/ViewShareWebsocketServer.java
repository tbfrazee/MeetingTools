package org.labkey.meetingtools;

import org.json.JSONObject;
import org.labkey.api.security.UserManager;

import javax.websocket.CloseReason;
import javax.websocket.EndpointConfig;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.ServerEndpoint;

/**
 * Endpoint for ViewShare websocket connections.
 */

@ServerEndpoint(value="/ViewShareServer", configurator=ChatWebsocketServer.Configurator.class)
public class ViewShareWebsocketServer extends AbstractWebsocketServer
{
    public ViewShareWebsocketServer()
    {
        _manager = MeetingToolsManager.getViewShare();
    }

    @OnOpen
    public void onOpen(Session session, EndpointConfig config)
    {
        //Connections handled with init message
    }

    @OnClose
    public void onClose(Session session, CloseReason reason)
    {
        //Disconnections handled with disconn message.
        //If that fails...try here?
        if(MeetingToolsManager.getViewShare().isSessionConnected(UserManager.getUser((int)session.getUserProperties().get("userId")), session))
            onMessage(session, "{type:'disconn'}");
    }

    public void processMessage(JSONObject inJson, JSONObject outJson, MessageMeta meta)
    {
        if(meta.getUser() != null)
        {
            switch (meta.getMsgType())
            {
                case "init":
                    if (!MeetingToolsManager.getViewShare().isSessionConnected(meta.getUser(), meta.getSession()))
                    {
                        //boolean isNewUserToRoom = MeetingToolsManager.getViewShare().isNewUserToRoom(meta.getUser(), meta.getRoom());
                        if(!inJson.optBoolean("isOwnerInit"))
                        {
                            if(!MeetingToolsManager.getViewShare().doesRoomExist(meta.getRoom()))
                            {
                                outJson.put("msgType", "error");
                                outJson.put("error", "The selected room does not exist.");
                                sendSingleMessage(meta.getSession(), outJson);
                                return;
                            }
                        }
                        MeetingToolsManager.getViewShare().addConnection(meta.getUser(), meta.getSession(), meta.getRoom());
                        outJson.put("msgType", "init");
                        outJson.put("queryConfig", MeetingToolsManager.getViewShare().getRoomConfig(meta.getRoom()));
                        outJson.put("users", MeetingToolsManager.getViewShare().getUsernamesForRoom(meta.getRoom()));

                        if(!sendSingleMessage(meta.getSession(), outJson))
                            return;

                        outJson.clear();
                        outJson.put("msgType", "newconn");
                        outJson.put("username", meta.getUser().getDisplayName(null));
                    }
                    break;
                case "newview":
                    if(meta.getUser().equals(MeetingToolsManager.getViewShare().getRoomOwner(meta.getRoom())))
                    {
                        MeetingToolsManager.getViewShare().setRoomConfig(meta.getRoom(), new JSONObject(inJson.getString("queryConfig")));
                        outJson.put("msgType", "newview");
                        outJson.put("queryConfig", inJson.getString("queryConfig"));
                        meta.setSendMessageToSender(false);
                    }
                    break;
                case "delroom":
                    MeetingToolsManager.getViewShare().closeRoom(meta.getRoom());
                    outJson.put("msgType", "delroom");
                    break;
                case "disconn":
                    meta.setRoom(MeetingToolsManager.getViewShare().getRoomForSession(meta.getUser(), meta.getSession()));
                    MeetingToolsManager.getViewShare().removeConnection(meta.getUser(), meta.getSession());
                    if(MeetingToolsManager.getViewShare().getRoomOwner(meta.getRoom()).equals(meta.getUser()))
                    {
                        JSONObject dJson = new JSONObject();
                        dJson.put("type", "delroom");
                        dJson.put("container", meta.getRoom().getKey().getPath());
                        dJson.put("roomName", meta.getRoom().getValue());

                        onMessage(meta.getSession(), dJson.toString());
                    }
                    if(!MeetingToolsManager.getViewShare().getUsersForRoom(meta.getRoom()).contains(meta.getUser()))
                    {
                        outJson.put("username", meta.getUser().getDisplayName(null));
                        outJson.put("msgType", "disconn");
                    }
                    break;
                default:
            }
        }
    }

    @OnError
    public void onError(Session session, Throwable error)
    {
        
    }




}
