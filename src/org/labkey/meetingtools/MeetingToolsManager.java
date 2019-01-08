/*
 * Copyright (c) 2017 LabKey Corporation
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.labkey.meetingtools;

import org.jetbrains.annotations.Nullable;
import org.json.JSONObject;
import org.labkey.api.data.Container;
import org.labkey.api.security.User;
import org.labkey.api.security.UserManager;
import org.labkey.api.util.Pair;

import javax.websocket.Session;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class MeetingToolsManager
{
    private static final MeetingToolsManager _instance = new MeetingToolsManager();
    private ChatManager _chat;
    private ViewShareManager _viewShare;
    private Class _CoreCollabServiceClass;

    private MeetingToolsManager()
    {
        // prevent external construction with a private default constructor
        _chat = new ChatManager();
        _viewShare = new ViewShareManager();
    }

    public static MeetingToolsManager get()
    {
        return _instance;
    }

    /**
     * Static shortcut to the instance's ChatManager
     * @return The ChatManager
     */
    public static ChatManager getChat()
    {
        return _instance._chat;
    }

    /**
     * Static shortcut to the instance's ViewShareManager
     * @return The ViewShareManager
     */
    public static ViewShareManager getViewShare()
    {
        return _instance._viewShare;
    }

    /**
     * Retrieves the ChatManager
     * @return ChatManager instance
     */
    public ChatManager getChatManager()
    {
        return _chat;
    }

    /**
     * Retrieves the ViewShareManager
     * @return ViewShareManager instance
     */
    public ViewShareManager getViewShareManager()
    {
        return _viewShare;
    }

    //Not yet implemented
    public void setCoreCollabServiceClass(@Nullable Class ccs)
    {
        _CoreCollabServiceClass = ccs;
    }

    /**
     * Abstract management class containing most of the tools needed for managing user connections to meetingtools
     * Not all implementations will need to add or override methods, but should be extensions of this class anyway
     */
    public abstract class AbstractMeetingToolsSubManager
    {
        protected Map<Integer, Map<Session, Pair<Container, String>>> _connections = new HashMap<>();

        public void addConnection(Integer userId, Session session, Container container, String roomName)
        {
            addConnection(userId, session, new Pair<>(container, roomName));
        }

        /**
         * Adds a user connection to a room
         * @param userId the new user's id
         * @param session the new user's session
         * @param room the room to which the new user should be added
         */
        public void addConnection(Integer userId, Session session, Pair<Container, String> room)
        {
            session.setMaxIdleTimeout(-1);
            Map<Session, Pair<Container, String>> sessionMap = _connections.get(userId);
            if (sessionMap != null)
                sessionMap.put(session, room);
            else
            {
                sessionMap = new HashMap<>();
                sessionMap.put(session, room);
                _connections.put(userId, sessionMap);
            }
        }

        public void addConnection(User user, Session session, Container container, String roomName)
        {
            addConnection(user.getUserId(), session, new Pair<>(container, roomName));
        }

        public void addConnection(User user, Session session, Pair<Container, String> room)
        {
            addConnection(user.getUserId(), session, room);
        }

        public void updateUserRoom(User user, Session session, Pair<Container, String> room)
        {
            updateUserRoom(user.getUserId(), session, room);
        }

        public void updateUserRoom(User user, Session session, Container container, String roomName)
        {
            updateUserRoom(user.getUserId(), session, new Pair<>(container, roomName));
        }

        public void updateUserRoom(Integer userId, Session session, Pair<Container, String> room)
        {
            Map<Session, Pair<Container, String>> sessionMap = _connections.get(userId);
            if (sessionMap != null)
            {
                sessionMap.put(session, room);
            }
        }

        public void updateUserRoom(Integer userId, Session session, Container container, String roomName)
        {
            updateUserRoom(userId, session, new Pair<>(container, roomName));
        }

        /**
         * Determines if a user is new to a room, or has been here/is reconnecting
         * @param user the user
         * @param room the room
         * @return true if the user is new to the room, else false
         */
        public boolean isNewUserToRoom(User user, Pair<Container, String> room)
        {
            Map<Session, Pair<Container, String>> userMap = _connections.get(user.getUserId());
            if (userMap != null)
            {
                for (Pair<Container, String> regRoom : userMap.values())
                {
                    if (regRoom.equals(room))
                        return false;
                }
            }
            return true;
        }

        /**
         * Removes a connection
         * @param userId the user's id to remove
         * @param session the session to remove
         */
        public void removeConnection(Integer userId, Session session)
        {
            for (Session s : _connections.get(userId).keySet())
            {
                if (s.equals(session))
                {
                    _connections.get(userId).remove(s);
                    if (_connections.get(userId) != null && _connections.get(userId).isEmpty())
                        _connections.remove(userId);
                }
            }
        }

        public void removeConnection(User user, Session session)
        {
            removeConnection(user.getUserId(), session);
        }

        public boolean isUserConnected(User user)
        {
            return _connections.containsKey(user.getUserId());
        }

        public boolean isSessionConnected(User user, Session session)
        {
            if (_connections.containsKey(user.getUserId()))
            {
                for (Session s : _connections.get(user.getUserId()).keySet())
                {
                    if (s.equals(session))
                        return true;
                }
            }
            return false;
        }

        /**
         * Finds the room for a given session
         * @param user the user associated with the session
         * @param session the session
         * @return the room associated with the session
         */
        public Pair<Container, String> getRoomForSession(User user, Session session)
        {
            Map<Session, Pair<Container, String>> sMap = _connections.get(user.getUserId());
            return (sMap == null) ? null : sMap.get(session);
        }

        public Set<User> getUsersForRoom(Container container, String room)
        {
            return getUsersForRoom(new Pair<>(container, room));
        }

        /**
         * Gets all users in a room
         * @param room the room
         * @return a Set of Users that are in room
         */
        public Set<User> getUsersForRoom(Pair<Container, String> room)
        {
            Set<User> ret = new HashSet<>();
            Pair<Container, String> comp = room;
            for (Map.Entry<Integer, Map<Session, Pair<Container, String>>> userCon : _connections.entrySet())
            {
                for (Map.Entry<Session, Pair<Container, String>> entry : userCon.getValue().entrySet())
                {
                    if (entry.getValue().equals(comp))
                        ret.add(UserManager.getUser(userCon.getKey()));
                }
            }
            return ret;
        }

        /**
         * Gets usernames for all users in a room
         * @param room the room
         * @return a set of String usernames, one for each user in the room
         */
        public Set<String> getUsernamesForRoom(Pair<Container, String> room)
        {
            Set<String> ret = new HashSet<>();
            Set<User> users = getUsersForRoom(room);
            for (User user : users)
            {
                ret.add(user.getDisplayName(null));
            }
            return ret;
        }

        public Set<String> getUsernamesForRoom(Container container, String roomName)
        {
            return getUsernamesForRoom(new Pair<>(container, roomName));
        }

        public Set<Session> getSessionsForRoom(Pair<Container, String> room)
        {
            return getSessionsForRoom(room.getKey(), room.getValue());
        }

        /**
         * Gets all sessions for users in a room
         * @param container the room's container
         * @param room the room's name
         * @return a Set of Sessions for all users in the room
         */
        public Set<Session> getSessionsForRoom(Container container, String room)
        {
            Set<Session> ret = new HashSet<>();
            Pair<Container, String> comp = new Pair<>(container, room);
            for (Map<Session, Pair<Container, String>> values : _connections.values())
            {
                for (Map.Entry<Session, Pair<Container, String>> entry : values.entrySet())
                {
                    if (entry.getValue().equals(comp))
                        ret.add(entry.getKey());
                }
            }
            return ret;
        }

        /**
         * Gets all sessions for a given container
         * @param container the container
         * @return a Set of Sessions for all users in all rooms for the container
         */
        public Set<Session> getSessionsForContainer(Container container)
        {
            Set<Session> ret = new HashSet<>();
            for (Map<Session, Pair<Container, String>> values : _connections.values())
            {
                for (Map.Entry<Session, Pair<Container, String>> entry : values.entrySet())
                {
                    Pair<Container, String> room = entry.getValue();
                    if (room.getKey().equals(container))
                        ret.add(entry.getKey());
                }
            }
            return ret;
        }

        /**
         * Gets all rooms for each container
         * @return a Map of key: Container, value: room name
         */
        public Map<Container, Set<String>> getRoomsByContainer()
        {
            Map<Container, Set<String>> ret = new HashMap<>();
            for (Map<Session, Pair<Container, String>> sessionMap : _connections.values())
            {
                for (Pair<Container, String> room : sessionMap.values())
                {
                    if (ret.containsKey(room.getKey()))
                        ret.get(room.getKey()).add(room.getValue());
                    else
                    {
                        Set<String> roomSet = new HashSet<>();
                        roomSet.add(room.getValue());
                        ret.put(room.getKey(), roomSet);
                    }
                }
            }
            return ret;
        }

        /**
         * Gets all room names for a given container
         * @param container the container
         * @return a Set of String room names for the container
         */
        public Set<String> getRoomNamesForContainer(Container container)
        {
            Map<Container, Set<String>> rooms = getRoomsByContainer();
            if (rooms.containsKey(container))
                return rooms.get(container);
            else
                return new HashSet<>();
        }

        public boolean shouldDeleteRoom(Container container, String roomName)
        {
            return shouldDeleteRoom(new Pair<>(container, roomName));
        }

        /**
         * Checks to see if a room is empty, and should therefore be deleted
         * @param room the room
         * @return true if the room is empty, else false
         */
        public boolean shouldDeleteRoom(Pair<Container, String> room)
        {
            if (!(room.getValue().equals("General")))
                return !getRoomNamesForContainer(room.getKey()).contains(room.getValue());
            else
                return false;
        }
    }

    /**
     * An implementation of AbstractMeetingToolsSubManager for handling chat rooms
     */
    public class ChatManager extends AbstractMeetingToolsSubManager
    {

    }

    /**
     * An implementation of AbstractMeetingToolsSubManager for handling query view sharing
     */
    public class ViewShareManager extends AbstractMeetingToolsSubManager
    {
        private Map<Pair<Container, String>, RoomConfig> _roomConfigs = new HashMap<>();

        /**
         * Gets a list of all rooms
         * @param container the container in which to check for rooms
         * @return a Set of String room names
         */
        public Set<String> getRoomList(Container container)
        {
            Set<String> ret = new HashSet<>();
            for(Pair<Container, String> room : _roomConfigs.keySet())
            {
                if(room.getKey().equals(container))
                {
                    ret.add(room.getValue());
                }
            }
            return ret;
        }

        /**
         * Checks if a room exists
         * @param room the room to check
         * @return true if the room exists, else false
         */
        public boolean doesRoomExist(Pair<Container, String> room)
        {
            return _roomConfigs.containsKey(room);
        }

        /**
         * Gets a room's owner
         * @param room the room
         * @return the User who owns the room
         */
        public User getRoomOwner(Pair<Container, String> room)
        {
            if(_roomConfigs.containsKey(room))
                return _roomConfigs.get(room).getOwner();
            else
                return null;
        }

        /**
         * Create a new room
         * @param room the room to create
         * @param owner the owner of the room
         */
        public void newRoom(Pair<Container, String> room, User owner)
        {
            RoomConfig cfg = new RoomConfig();
            cfg.setOwner(owner);
            cfg.setCreated(new Date());
            cfg.setTitle(room.getValue());
            _roomConfigs.put(room, cfg);
        }

        /**
         * Close a room
         * @param room the room to close
         */
        public void closeRoom(Pair<Container, String> room)
        {
            _roomConfigs.remove(room);
        }

        /**
         * Changes a room's owner
         * @param room the room
         * @param newOwner the new owner
         */
        public void changeRoomOwner(Pair<Container, String> room, User newOwner)
        {
            if(_roomConfigs.containsKey(room))
            {
                _roomConfigs.get(room).setOwner(newOwner);
            }
        }

        /**
         * Gets a room's config
         * @param room the room
         * @return a JSONObject of the config
         */
        public JSONObject getRoomConfig(Pair<Container, String> room)
        {
            RoomConfig cfg = _roomConfigs.get(room);
            if(cfg != null)
                return cfg.getConfig();
            else
                return null;
        }

        /**
         * Sets a room's config
         * @param room the room
         * @param config the JSONObject config to set
         */
        public void setRoomConfig(Pair<Container, String> room, JSONObject config)
        {
            RoomConfig cfg = _roomConfigs.get(room);
            if(cfg != null)
            {
                cfg.setConfig(config);
            }
        }

        /**
         * A struct for ViewShare room data
         */
        private class RoomConfig
        {
            private String _title;
            private User _owner;
            private Date _created;
            private JSONObject _config;

            public String getTitle()
            {
                return _title;
            }

            public void setTitle(String title)
            {
                _title = title;
            }

            public User getOwner()
            {
                return _owner;
            }

            public void setOwner(User owner)
            {
                _owner = owner;
            }

            public Date getCreated()
            {
                return _created;
            }

            public void setCreated(Date created)
            {
                _created = created;
            }

            public JSONObject getConfig()
            {
                return _config;
            }

            public void setConfig(JSONObject config)
            {
                _config = config;
            }
        }
    }
}