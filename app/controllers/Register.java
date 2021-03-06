package controllers;

import helpers.InSitemap;

import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import models.Invitation;
import models.InvitedSubscribedNotification;
import models.Promocode;
import models.User;
import models.Vote.State;
import models.analytics.UserClientInfo;
import notifiers.Mails;
import play.Logger;
import play.cache.Cache;
import play.data.validation.Email;
import play.data.validation.Equals;
import play.data.validation.Match;
import play.data.validation.MinSize;
import play.data.validation.Required;
import play.i18n.Messages;
import play.mvc.Controller;
import play.mvc.Router;

public class Register extends Controller {

	@InSitemap(changefreq="monthly", priority=1)
	public static void register(String email, String username) {
		render(email, username);
	}
	
	// TODO : I18N
	public static void registerNew(@Required @Email String email, @Required @Match(value="[a-zA-Z0-9_]{3,16}", message="username has to be 3-16 chars, no space, no accent and no punctuation") String username, @Required @MinSize(5) String password) throws Throwable {
		// Check if username or email not already in use, because username and email must be unique !
		if(!User.isEmailAvailable(email)) {
			validation.addError("email", Messages.get("registeremailexist")); 
		}
		if(!User.isUsernameAvailable(username)) {
			validation.addError("username", Messages.get("registerusernameexist")); 
		}
		
		if (validation.hasErrors()) {
	        validation.keep();
	        register(email, username);
	    }
		
		Logger.info("Register: " + email + "/" + username);
		User user = new User(email, username, password);
		user.isPromocodeValidated = true;
		user.save();
		
		// send an email confirmation mail
		Mails.confirmation(user);
		
		// Check if this new user was invited by an existing user with the invitation system
		List<Invitation> invitations = Invitation.find("invitedEmail = ?", email).fetch();
		for(Invitation invitation : invitations) {
			// add this new user to the invitor's favorites
			invitation.invitor.followedUsers.add(user);
			invitation.invitor.save();
			
			// add the invitor's to the new user's favorites
			user.followedUsers.add(invitation.invitor);
			// create a notification
			InvitedSubscribedNotification notif = new InvitedSubscribedNotification(invitation.invitor, user);
			notif.save();
			// send a mail
			Mails.invitedSubscribedNotification(notif);
		}
		user.save();
		
		// connect immediately the user
		Secure.authenticate(email, password, false);
	}
	
	/**
	 * AJAX
	 * Is the given userName available on beansight.com?
	 * @return : true if the userName is available, false otherwise
	 */
	public static void isUserNameAvailable(String username) {
		boolean available = false;
		
		User user = CurrentUser.getCurrentUser();
		if( user != null && username.equals(user.userName)) {
			available = true;
		} else {
			available = User.isUsernameAvailable(username);
		}
		
		if(available) {
			renderJSON(available);
		} else {
			renderJSON("\""+ Messages.get("registerusernameexist") + "\"");
		}
	}
	
	/**
	 * Is the given email available on beansight.com?
	 * @return : true if the email is available, false otherwise
	 */
	public static void isEmailAvailable(String email) {
		boolean available = User.isEmailAvailable(email);
		if(available) {
			renderJSON(available);
		} else {
			renderJSON("\""+ Messages.get("registeremailexist") + "\"");
		}
	}
	
	public static void extAuthFirstTimeConnectPage( String email, String username) {
		if(Security.isConnected() && !CurrentUser.getCurrentUser().isPromocodeValidated) {// if the user already has a valid account, do not show this
			render(email, username);
		} else {
			Application.index();
		}
	}
	
	
	public static void firstTimeConnect(
		@Required @Email String email, 
		@Required @Match(value="[a-zA-Z0-9_]{3,16}", message="username has to be 3-16 chars, no space, no accent and no punctuation") String username) {
		
		if(Security.isConnected()) {
			User currentUser = CurrentUser.getCurrentUser();
			// if the user have change its username check that it's available
			if (!username.equalsIgnoreCase(currentUser.userName)) {
				if (!User.isUsernameAvailable(username)) {
					validation.addError("username", Messages.get("registerusernameexist")); 
				}
			}
			
			// if the user have change its email check that it's available
			if (!email.equalsIgnoreCase(currentUser.email)) {
				if (!User.isEmailAvailable(email)) {
					validation.addError("email", Messages.get("registeremailexist")); 
				}
			}
			
			if (validation.hasErrors()) {
				validation.keep();
				extAuthFirstTimeConnectPage(email, username);
		    }
			
			currentUser.email = email;
			currentUser.userName = username;
			currentUser.isPromocodeValidated = true;
			currentUser.save();
		
			// send an email confirmation mail
			Mails.confirmation(currentUser);
		} 
		
		// redirect to the previous url or index if nothing was set in session
		if (session.contains("url")) {
			String url = session.get("url");
			session.remove("url");
			redirect(url);
		} else {
			Application.index();
		}
	}
	
	/** Confirm that the email address of the user is a real one */
	public static void confirm(String uuid) {
		if(uuid == null || uuid.isEmpty()) {
			notFound();
		}
		User user = User.find("byUuid", uuid).first();
		notFoundIfNull(user);
		user.emailConfirmed = true;
		user.save();
		
		Logger.info("Email confirmation for user : " + user.email);
		
		// set the message in the session not in flash because there is some redirect happening here 
		session.put("beansight_msg", Messages.get("emailconfirmed"));
		
		Application.index();
	}
	
	public static void fbAuthenticate(String url) {
		// add url in session to redirect back the user to the url he was browsing before authenticate 
		if (session.get("url") ==null) {
			session.put("url", url);
		}
		FacebookOAuth.authenticate();
	}
	
	public static void twitAuthenticate(String url) throws Exception {
		// add url in session to redirect back the user to the url he was browsing before authenticate 
		if (session.get("url") ==null) {
			session.put("url", url);
		}
		TwitterOAuth.authenticate();
	}
	
	public static void beansightAuthenticate(@Required String username, String password, boolean remember, String url) throws Throwable {
		// add url in session to redirect back the user to the url he was browsing before authenticate 
		if (flash.get("url") ==null) {
			flash.put("url", url);
		}
		Secure.authenticate(username, password, remember);
	}
	
	public static void linkBeansightAccountWithFacebook() {
		// add an info in session so that we know it's for synchronizing with facebook account
		session.remove(FacebookOAuthForBeansight.FACEBOOK_SYNC_COOKIE);
		session.put(FacebookOAuthForBeansight.LINK_FACEBOOK_TO_BEANSIGHT_COOKIE, "true");
		
		// add url in session to redirect back the user to the page for managing facebook friends 
		session.put("url", Router.getFullUrl("Application.manageFacebookFriends"));
		
		// redirect to Facebook authentication
		FacebookOAuth.authenticate();
	}
}
