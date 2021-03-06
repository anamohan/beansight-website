package controllers;

import gson.FacebookUserGson;
import gson.FriendGson;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Type;
import java.util.LinkedList;
import java.util.List;

import models.FacebookUser;
import models.User;
import play.Logger;
import play.Play;
import play.libs.WS;
import play.mvc.Controller;
import play.mvc.Router;
import play.mvc.results.Redirect;
import play.utils.Java;
import services.FacebookServices;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

/**
 * 
 * @author jb
 *
 */
public class FacebookOAuth extends Controller {

    /**
     * client_id
     */
    private static final String FB_CLIENT_ID = Play.configuration.getProperty("facebook.client_id"); 
    
    /**
     * client_secret
     */
    private static final String FB_APPLICATION_SECRET = Play.configuration.getProperty("facebook.client_secret");
    
    /**
     * API key
     */
    private static final String FB_API_KEY = Play.configuration.getProperty("facebook.api_key"); 
    
    
    public static void authenticate() {
    	// uncomment this to enable facebook login
		StringBuilder fbAuthenticateUrl = new StringBuilder();
		fbAuthenticateUrl
				.append("https://graph.facebook.com/oauth/authorize?client_id=")
				.append(FB_CLIENT_ID).append("&redirect_uri=")
				.append(Router.getFullUrl(request.controller + ".callback"));
				
		// request extended permissions (email, ...)
		String extendedPermissions = null;
		try {
			extendedPermissions = (String) FacebookOAuthDelegate
					.invoke("getExtendedPermissions");
		} catch (Throwable e) {
			Logger.error("Failed calling getExtendedPermissions", e);
		}
		if (extendedPermissions != null
				&& extendedPermissions.trim().length() != 0) {
			fbAuthenticateUrl.append("&scope=");
			fbAuthenticateUrl.append(extendedPermissions);
		}
		
        throw new Redirect(fbAuthenticateUrl.toString());
    }
    
    /**
     * called by facebook after being authenticated 
     * @throws Throwable 
     */
    public static void callback(String code) throws Throwable {
		StringBuilder fbAccessTokenUrl = new StringBuilder();
		fbAccessTokenUrl
				.append("https://graph.facebook.com/oauth/access_token?client_id=")
				.append(FB_CLIENT_ID).append("&redirect_uri=")
				.append(Router.getFullUrl(request.controller + ".callback"))
				.append("&client_secret=").append(FB_APPLICATION_SECRET)
				.append("&code=").append(WS.encode(code.replace("|","%7C")));
        
        String response = WS.url(fbAccessTokenUrl.toString()).post().getString();
        
        String accessToken = response.split("=")[1].split("&")[0];
        //session.put("fb", accessToken); // I think the accessToken should be kept secret so it's not a good idea to put in the cookie
        
    	FacebookServices facebookServices = new FacebookServices(accessToken);
    	FacebookUserGson facebookModelObject = facebookServices.getFacebookModelObject();
        

    	saveBasicFacebookUserInfosAndFriends(accessToken, facebookModelObject);
    	
        FacebookOAuthDelegate.invoke("onFacebookAuthentication", accessToken, facebookModelObject);
    }
    
    
    /**
     * Saves in DB the friends of the current facebook user 
     * 
     * @param accessToken
     * @param facebookUserGson
     */
    static FacebookUser saveBasicFacebookUserInfosAndFriends(String accessToken, FacebookUserGson facebookUserGson) {
    	FacebookServices facebookServices = new FacebookServices(accessToken);
    	List<FriendGson> friends = facebookServices.getFriends(facebookUserGson.getId());
		
    	// check that the current user has an entry in DB, if not add it
		FacebookUser fbUser = FacebookUser.findByFacebookId(facebookUserGson.getId());
		if (fbUser == null) {
			fbUser = new FacebookUser(facebookUserGson.getId());
			fbUser.save();
		}

		//
		// save basic user infos
		//
		fbUser.name = facebookUserGson.getName();
		fbUser.firstName = facebookUserGson.getFirst_name();
		fbUser.lastName = facebookUserGson.getLast_name();
		fbUser.link = facebookUserGson.getLink();
		fbUser.gender = facebookUserGson.getGender();
		fbUser.timezone = facebookUserGson.getTimezone();
		fbUser.locale = facebookUserGson.getLocale();
		fbUser.verified = facebookUserGson.isVerified();
		fbUser.updateTime = facebookUserGson.getUpdate_time();
		
		//
		// save friends
		//
		for (FriendGson f : friends) {
			// if not already in the user's friends add it
			if (!fbUser.isThisFacebookUserAlreadyMyFriend(f.getUid1())) {
				// get the FacebookUser if it already exists :
				FacebookUser fbFriend = FacebookUser.findByFacebookId(f
						.getUid1());
				if (fbFriend == null) {
					fbFriend = new FacebookUser(f.getUid1());
				}
				fbUser.friends.add(fbFriend);
			}
		}
		
		fbUser.save();
		return fbUser;
    }
    
     
    // I can't remember if this method is useful ? ...
    public static void acccessTokenCallback(String accessToken) {
        throw new Redirect(Router.getFullUrl("Application.index"));
    }

    
    /**
     * In the client application, extend FacebookOAuthDelegate to re define onFacebookAuthentication method
     * 
     * @author jb
     *
     */
    public static class FacebookOAuthDelegate extends Controller {
        
    	/**
    	 * Override this method to handle the returning data from facebook containing
    	 * the user informations.
    	 * 
    	 * @param facebookModelObject
    	 */
        static void onFacebookAuthentication(String accessToken, FacebookUserGson facebookUserGson)  {
            Application.index();
        }
        
        /**
         * Override this method if you want to get extended permission about users informations.
         * See http://developers.facebook.com/docs/authentication/
         * 
         * @return a comma separated permissions
         */
        static String getExtendedPermissions() {
        	return "";
        }
        
        private static Object invoke(String m, Object... args) throws Throwable {
            Class facebookOAuthDelegate = null;
            List<Class> classes = Play.classloader.getAssignableClasses(FacebookOAuthDelegate.class);
            if(classes.size() == 0) {
                facebookOAuthDelegate = FacebookOAuthDelegate.class;
            } else {
                facebookOAuthDelegate = classes.get(0);
            }
            try {
                return Java.invokeStaticOrParent(facebookOAuthDelegate, m, args);
            } catch(InvocationTargetException e) {
                throw e.getTargetException();
            }
        }
    }
    
}
