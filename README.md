App Creator
===========

This is an application for [AppRunner](https://github.com/danielflower/app-runner)
which makes it easy to create a new repo on your GitHub page with a sample app
and register it with AppRunner.

Installation
------------

Add this repo's git URL to your AppRunner instance. The first time it will fail to start due to
missing OAuth settings.

OAuth is used to create repos on users' GitHub accounts. Go to 
[developer settings in itHub](https://github.com/settings/developers) and add App-Creator as an
OAuth App. Use App Migrator's full URL, as it appears on your AppRunner instance, as both the
app homepage and callback URL.

Once created, you will be given an app client ID and a secret. Set those as environment variables
called `APP_CREATOR_GITHUB_OAUTH_CLIENT_ID` and `APP_CREATOR_GITHUB_OAUTH_CLIENT_SECRET` 

Restart AppRunner.
