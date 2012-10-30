// CloudCoder - a web-based pedagogical programming environment
// Copyright (C) 2011-2012, Jaime Spacco <jspacco@knox.edu>
// Copyright (C) 2011-2012, David H. Hovemeyer <david.hovemeyer@gmail.com>
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU Affero General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU Affero General Public License for more details.
//
// You should have received a copy of the GNU Affero General Public License
// along with this program.  If not, see <http://www.gnu.org/licenses/>.

package org.cloudcoder.app.client.page;

import java.util.ArrayList;
import java.util.List;

import org.cloudcoder.app.client.CloudCoder;
import org.cloudcoder.app.client.model.Session;
import org.cloudcoder.app.client.model.StatusMessage;
import org.cloudcoder.app.client.rpc.RPC;
import org.cloudcoder.app.client.view.SessionExpiredDialogBox;
import org.cloudcoder.app.shared.model.Activity;
import org.cloudcoder.app.shared.model.User;
import org.cloudcoder.app.shared.util.DefaultSubscriptionRegistrar;
import org.cloudcoder.app.shared.util.SubscriptionRegistrar;

import com.google.gwt.user.client.rpc.AsyncCallback;
import com.google.gwt.user.client.ui.IsWidget;

/**
 * Common superclass for all CloudCoder "pages".
 * Provides helper methods for managing session data and event subscribers.
 * Also provides support for allowing the user to log back in
 * when a server-side session timeout occurs.
 */
public abstract class CloudCoderPage {
	private List<Class<?>> sessionObjectClassList;
	private DefaultSubscriptionRegistrar subscriptionRegistrar;
	private Session session;
	
	/**
	 * Constructor.
	 */
	public CloudCoderPage() {
		this.sessionObjectClassList = new ArrayList<Class<?>>();
		this.subscriptionRegistrar = new DefaultSubscriptionRegistrar();
	}

	/**
	 * Set the Session object that the page should use.
	 * 
	 * @param session the Session object
	 */
	public void setSession(Session session) {
		this.session = session;
	}
	
	/**
	 * Subclasses may call this method when a server-side session
	 * timeout occurs. It will allow the user to log back in,
	 * establishing a new valid server-side session if successful.
	 */
	public void recoverFromServerSessionTimeout(final Runnable successfulRecoveryCallback) {
		final SessionExpiredDialogBox dialog = new SessionExpiredDialogBox();
		
		Runnable callback = new Runnable() {
			@Override
			public void run() {
				String password = dialog.getPassword();
				
				// Try to log back in.
				RPC.loginService.login(session.get(User.class).getUsername(), password, new AsyncCallback<User>() {
					@Override
					public void onSuccess(User result) {
						session.add(StatusMessage.goodNews("Successfully logged back in"));
						
						// Try to set the Activity in the server-side session.
						Activity activity = CloudCoder.getActivityForSessionAndPage(CloudCoderPage.this, session);
						RPC.loginService.setActivity(activity, new AsyncCallback<Void>() {
							@Override
							public void onFailure(Throwable caught) {
								// Not a fatal problem: we're logged in, but for some reason
								// we couldn't set the activity in the server-side session.
								session.add(StatusMessage.information("Logged back in, but couldn't set activity on server"));
								dialog.hide();
								successfulRecoveryCallback.run();
							}

							@Override
							public void onSuccess(Void result) {
								// At this point, we are completely logged back in an
								// we have a valid Activity set on the server.
								dialog.hide();
								successfulRecoveryCallback.run();
							}
						});
					}
					
					@Override
					public void onFailure(Throwable caught) {
						dialog.setError("Could not log in: " + caught.getMessage());
					}
				});
			}
		};
		
		dialog.setLoginButtonHandler(callback);
		
		dialog.center();
	}
	
	/**
	 * Add an object to the Session.
	 * When this page is finished, the object (or any object of the
	 * same type which replaced the original object) will be removed
	 * from the Session.
	 * 
	 * @param obj  object to add to the Session
	 */
	protected void addSessionObject(Object obj) {
		session.add(obj);
		sessionObjectClassList.add(obj.getClass());
	}
	
	/**
	 * Remove all objects added to the Session.
	 */
	protected void removeAllSessionObjects() {
		for (Class<?> cls : sessionObjectClassList) {
			session.remove(cls);
		}
	}

	/**
	 * Create this page's widget.
	 */
	public abstract void createWidget();
	
	/**
	 * This method is called after the page's UI has been
	 * added to the DOM tree.
	 */
	public abstract void activate();
	
	/**
	 * This method is called just before the UI is removed
	 * from the client web page.  Subclasses may override this
	 * to do cleanup.
	 */
	public abstract void deactivate();

	/**
	 * @return the widget that is the UI for this page 
	 */
	public abstract IsWidget getWidget();
	
	/**
	 * Check whether this page is an "activity": meaning that
	 * if the user closes the page and navigates back, that
	 * the same page should be restored (if the server session is
	 * still valid.)
	 * 
	 * @return true if the page is an activity, false if not
	 */
	public abstract boolean isActivity();
	
	/**
	 * @return the Session object
	 */
	public Session getSession() {
		return session;
	}
	
	/**
	 * Get the SubscriptionRegistrar which keeps track of subscribers
	 * for this view.
	 * 
	 * @return the SubscriptionRegistrar
	 */
	public SubscriptionRegistrar getSubscriptionRegistrar() {
		return subscriptionRegistrar;
	}
}
