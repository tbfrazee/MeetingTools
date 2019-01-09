# MeetingTools - A LabKey Module to Support Collaborative Meetings

## Overview
MeetingTools is comprised of 2 Web Parts - Chat and View Share - that are designed to assist in virtual meetings and conference calls between collaborators.

## Chat
The Chat web part connects to a simple websocket-based chat server. Chat rooms are folder-specific - two folders on the same server can have rooms with the same name, and they will remain separate rooms.

## View Share
The View Share web part allows users to create a room, call up grid views, and share that view with the other users in that room.

## Installation
Download the repository and use gradlew:meetingtools:deployModule within the Labkey project directory to build the module and deploy it to your server.

Pre-compiled module files are coming soon.

## Usage
Both the Chat and View Share web parts are available under the web part list. A folder administrator can add them to any folder for use.
