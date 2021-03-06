/*
 * Copyright (c) Azureus Software, Inc, All Rights Reserved.
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.activity;

import com.biglybt.android.client.*;
import com.biglybt.android.client.adapter.ProfileArrayAdapter;
import com.biglybt.android.client.dialog.DialogFragmentAbout;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile;
import com.biglybt.android.client.dialog.DialogFragmentGenericRemoteProfile.GenericRemoteProfileListener;
import com.biglybt.android.client.dialog.DialogFragmentGiveback;
import com.biglybt.android.client.rpc.RPC;
import com.biglybt.android.client.session.RemoteProfile;
import com.biglybt.android.client.session.RemoteProfileFactory;
import com.biglybt.android.util.BiglyCoreUtils;
import com.biglybt.android.util.FileUtils;
import com.biglybt.android.util.OnClearFromRecentService;
import com.biglybt.util.Thunk;

import android.app.Activity;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AlertDialog;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.*;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.AdapterView.AdapterContextMenuInfo;
import android.widget.Button;
import android.widget.ListView;

/**
 * Profile Selector screen and Main Intent
 */
public class IntentHandler
	extends ThemedActivity
	implements GenericRemoteProfileListener,
	AppPreferences.AppPreferencesChangedListener

{

	private static final String TAG = "ProfileSelector";

	private ListView listview;

	@Thunk
	ProfileArrayAdapter adapter;

	private Boolean isLocalVuzeAvailable = null;

	private Boolean isLocalVuzeRemoteAvailable = null;

	@Override
	protected String getTag() {
		return TAG;
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		appInit();

		final Intent intent = getIntent();

		if (handleIntent(intent, savedInstanceState)) {
			return;
		}

		setContentView(AndroidUtils.isTV() ? R.layout.activity_intent_handler_tv
				: R.layout.activity_intent_handler);

		listview = findViewById(R.id.lvRemotes);
		assert listview != null;
		listview.setItemsCanFocus(false);

		adapter = new ProfileArrayAdapter(this);

		listview.setAdapter(adapter);

		if (AndroidUtils.DEBUG) {
			Log.d("TUX1", "DS: " + intent.getDataString());
		}

		listview.setOnItemClickListener(new AdapterView.OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> parent, final View view,
					int position, long id) {
				Object item = parent.getItemAtPosition(position);

				if (item instanceof RemoteProfile) {
					RemoteProfile remote = (RemoteProfile) item;
					boolean isMain = IntentHandler.this.getIntent().getData() != null;
					RemoteUtils.openRemote(IntentHandler.this, remote, isMain, isMain);
				}
			}

		});

		Toolbar toolBar = findViewById(R.id.actionbar);
		if (toolBar != null) {
			setSupportActionBar(toolBar);
		}

		ActionBar actionBar = getSupportActionBar();
		if (actionBar != null) {
			actionBar.setDisplayShowHomeEnabled(true);
			actionBar.setDisplayUseLogoEnabled(true);
			actionBar.setIcon(R.drawable.biglybt_logo_toolbar);
		}

		Button btnAdd = findViewById(R.id.button_profile_add);
		if (btnAdd != null) {
			btnAdd.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					Intent myIntent = new Intent(getIntent());
					myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
					myIntent.setClass(IntentHandler.this, LoginActivity.class);
					startActivity(myIntent);
				}
			});
		}

		Button btnImport = findViewById(R.id.button_profile_import);
		if (btnImport != null) {
			btnImport.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					FileUtils.openFileChooser(IntentHandler.this,
							"application/octet-stream",
							TorrentViewActivity.FILECHOOSER_RESULTCODE);
				}
			});
		}
		Button btnExport = findViewById(R.id.button_profile_export);
		if (btnExport != null) {
			btnExport.setOnClickListener(new View.OnClickListener() {
				@Override
				public void onClick(View v) {
					AppPreferences.exportPrefs(IntentHandler.this);
				}
			});
		}
		registerForContextMenu(listview);
	}

	private void appInit() {
		startService(
				new Intent(getApplicationContext(), OnClearFromRecentService.class));
	}

	@Override
	public void appPreferencesChanged() {
		runOnUiThread(new Runnable() {
			public void run() {
				if (adapter != null) {
					adapter.refreshList();
				}
			}
		});
	}

	private boolean handleIntent(Intent intent,
			@Nullable Bundle savedInstanceState) {
		boolean forceProfileListOpen = false;

		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "ForceOpen? " + forceProfileListOpen);
			Log.d(TAG, "IntentHandler intent = " + intent);
		}

		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();

		Uri data = intent.getData();
		if (data != null) {
			try {
				// check for vuze://remote//*
				String scheme = data.getScheme();
				String host = data.getHost();
				String path = data.getPath();
				if (("vuze".equals(scheme) || "biglybt".equals(scheme))
						&& "remote".equals(host) && path != null && path.length() > 1) {
					String ac = path.substring(1);
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "got ac '" + ac + "' from " + data);
					}

					intent.setData(null);
					if (ac.equals("cmd=advlogin")) {
						DialogFragmentGenericRemoteProfile dlg = new DialogFragmentGenericRemoteProfile();
						AndroidUtilsUI.showDialog(dlg, getSupportFragmentManager(),
								DialogFragmentGenericRemoteProfile.TAG);
						forceProfileListOpen = true;
					} else if (ac.length() < 100) {
						RemoteProfile remoteProfile = RemoteProfileFactory.create("vuze",
								ac);
						return RemoteUtils.openRemote(this, remoteProfile, true, true);
					}
				}

				// check for http[s]://remote.vuze.com/ac=*
				if (host != null && host.equals("remote.vuze.com")
						&& data.getQueryParameter("ac") != null) {
					String ac = data.getQueryParameter("ac");
					if (AndroidUtils.DEBUG) {
						Log.d(TAG, "got ac '" + ac + "' from " + data);
					}
					intent.setData(null);
					if (ac.length() < 100) {
						RemoteProfile remoteProfile = RemoteProfileFactory.create("vuze",
								ac);
						return RemoteUtils.openRemote(this, remoteProfile, true, true);
					}
				}
			} catch (Exception e) {
				if (AndroidUtils.DEBUG) {
					e.printStackTrace();
				}
			}
		}

		if (!forceProfileListOpen) {
			boolean clearTop = (intent.getFlags()
					& Intent.FLAG_ACTIVITY_CLEAR_TOP) > 0;
			int numRemotes = getRemotesWithLocal().length;
			if (numRemotes == 0) {
				// New User: Send them to Login (Account Creation)
				Intent myIntent = new Intent(Intent.ACTION_VIEW, null, this,
						LoginActivity.class);
				myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION
						| Intent.FLAG_ACTIVITY_PREVIOUS_IS_TOP);

				startActivity(myIntent);
				finish();
				return true;
			} else if (!clearTop && (numRemotes == 1 || intent.getData() == null)) {
				try {
					RemoteProfile remoteProfile = appPreferences.getLastUsedRemote();
					if (remoteProfile != null) {
						if (savedInstanceState == null) {
							return RemoteUtils.openRemote(this, remoteProfile, true, true);
						}
					} else {
						Log.d(TAG, "Has Remotes, but no last remote");
					}
				} catch (Throwable t) {
					if (AndroidUtils.DEBUG) {
						Log.e(TAG, "onCreate", t);
					}
					AnalyticsTracker.getInstance(this).logError(t);
				}
			}
		}
		return false;
	}

	@Override
	protected void onNewIntent(Intent intent) {
		super.onNewIntent(intent);
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onNewIntent " + intent);
		}
		setIntent(intent);
		handleIntent(intent, null);
	}

	private RemoteProfile[] getRemotesWithLocal() {
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		RemoteProfile[] remotes = appPreferences.getRemotes();

		if (isLocalVuzeAvailable == null) {
			isLocalVuzeAvailable = RPC.isLocalAvailable(RPC.LOCAL_VUZE_PORT);
		}
		if (isLocalVuzeRemoteAvailable == null) {
			isLocalVuzeRemoteAvailable = RPC.isLocalAvailable(
					RPC.LOCAL_VUZE_REMOTE_PORT);
		}
		if (isLocalVuzeAvailable) {
			remotes = addLocalRemoteToArray(remotes, RPC.LOCAL_VUZE_PORT,
					R.string.local_vuze_name);
		}
		if (isLocalVuzeRemoteAvailable) {
			remotes = addLocalRemoteToArray(remotes, RPC.LOCAL_VUZE_REMOTE_PORT,
					R.string.local_vuze_remote_name);
		}
		return remotes;
	}

	private RemoteProfile[] addLocalRemoteToArray(RemoteProfile[] remotes,
			int port, int resNickID) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "Local BiglyBT Detected");
		}

		boolean alreadyAdded = false;
		for (RemoteProfile remoteProfile : remotes) {
			if (remoteProfile.getRemoteType() == RemoteProfile.TYPE_NORMAL
					&& "localhost".equals(remoteProfile.getHost())) {
				alreadyAdded = true;
				break;
			}
		}
		if (!alreadyAdded) {
			if (AndroidUtils.DEBUG) {
				Log.d(TAG, "Adding localhost profile..");
			}
			RemoteProfile localProfile = RemoteProfileFactory.create(
					RemoteProfile.TYPE_NORMAL);
			localProfile.setHost("localhost");
			localProfile.setPort(port);
			localProfile.setNick(getString(resNickID,
					BiglyBTApp.deviceName == null ? Build.MODEL : BiglyBTApp.deviceName));
			RemoteProfile[] newRemotes = new RemoteProfile[remotes.length + 1];
			newRemotes[0] = localProfile;
			System.arraycopy(remotes, 0, newRemotes, 1, remotes.length);
			remotes = newRemotes;
		}
		return remotes;
	}

	@Override
	protected void onPause() {
		super.onPause();
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.removeAppPreferencesChangedListener(this);
		isLocalVuzeRemoteAvailable = null;
		isLocalVuzeAvailable = null;
		AnalyticsTracker.getInstance(this).activityPause(this);
	}

	@Override
	protected void onResume() {
		super.onResume();

		if (adapter != null) {
			RemoteProfile[] remotesWithLocal = getRemotesWithLocal();
			adapter.addRemotes(remotesWithLocal);
		}
		AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
		appPreferences.addAppPreferencesChangedListener(this);
		AnalyticsTracker.getInstance(this).activityResume(this);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.menu_intenthandler_top, menu);
		return true;
	}

	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem itemAddCoreProfile = menu.findItem(R.id.action_add_core_profile);
		if (itemAddCoreProfile != null) {
			itemAddCoreProfile.setVisible(BiglyCoreUtils.isCoreAllowed()
					&& RemoteUtils.getCoreProfile() == null);
		}

		return super.onPrepareOptionsMenu(menu);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		int itemId = item.getItemId();
		if (itemId == R.id.action_add_profile) {
			Intent myIntent = new Intent(getIntent());
			myIntent.setFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION);
			myIntent.setClass(IntentHandler.this, LoginActivity.class);
			startActivity(myIntent);
			return true;
		} else if (itemId == R.id.action_add_adv_profile) {
			return AndroidUtilsUI.showDialog(new DialogFragmentGenericRemoteProfile(),
					getSupportFragmentManager(), DialogFragmentGenericRemoteProfile.TAG);
		} else if (itemId == R.id.action_add_core_profile) {
			RemoteUtils.createCoreProfile(this,
					new RemoteUtils.OnCoreProfileCreated() {
						@Override
						public void onCoreProfileCreated(RemoteProfile coreProfile,
								boolean alreadyCreated) {
							RemoteUtils.editProfile(coreProfile, getSupportFragmentManager());
						}
					});
		} else if (itemId == R.id.action_about) {
			return AndroidUtilsUI.showDialog(new DialogFragmentAbout(),
					getSupportFragmentManager(), "About");
		} else if (itemId == R.id.action_giveback) {
			DialogFragmentGiveback.openDialog(this, getSupportFragmentManager(), true,
					TAG);
			return true;
		} else if (itemId == R.id.action_export_prefs) {
			AppPreferences.exportPrefs(this);
		} else if (itemId == R.id.action_import_prefs) {
			FileUtils.openFileChooser(this, "application/octet-stream",
					TorrentViewActivity.FILECHOOSER_RESULTCODE);
		}

		return super.onOptionsItemSelected(item);
	}

	@Override
	public void onActivityResult(int requestCode, int resultCode, Intent intent) {
		if (AndroidUtils.DEBUG) {
			Log.d(TAG, "onActivityResult: " + requestCode + "/" + resultCode);
		}
		if (requestCode == TorrentViewActivity.FILECHOOSER_RESULTCODE) {
			Uri uri = intent == null || resultCode != Activity.RESULT_OK ? null
					: intent.getData();
			if (uri == null) {
				return;
			}
			AppPreferences.importPrefs(this, uri);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);

		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;
		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (item instanceof RemoteProfile) {
			MenuInflater inflater = getMenuInflater();
			inflater.inflate(R.menu.menu_context_intenthandler, menu);
		}
	}

	@Override
	public boolean onContextItemSelected(MenuItem menuitem) {
		ContextMenuInfo menuInfo = menuitem.getMenuInfo();
		AdapterContextMenuInfo adapterMenuInfo = (AdapterContextMenuInfo) menuInfo;

		Object item = listview.getItemAtPosition(adapterMenuInfo.position);

		if (!(item instanceof RemoteProfile)) {
			return super.onContextItemSelected(menuitem);
		}

		final RemoteProfile remoteProfile = (RemoteProfile) item;

		int itemId = menuitem.getItemId();
		if (itemId == R.id.action_edit_pref) {
			RemoteUtils.editProfile(remoteProfile, getSupportFragmentManager());
			return true;
		} else if (itemId == R.id.action_delete_pref) {
			new AlertDialog.Builder(this).setTitle("Remove Profile?").setMessage(
					"Configuration settings for profile '" + remoteProfile.getNick()
							+ "' will be deleted.").setPositiveButton("Remove",
									new DialogInterface.OnClickListener() {
										public void onClick(DialogInterface dialog, int which) {
											AppPreferences appPreferences = BiglyBTApp.getAppPreferences();
											appPreferences.removeRemoteProfile(remoteProfile.getID());
										}
									}).setNegativeButton(android.R.string.cancel,
											new DialogInterface.OnClickListener() {
												public void onClick(DialogInterface dialog, int which) {
												}
											}).setIcon(android.R.drawable.ic_dialog_alert).show();
			return true;
		}
		return super.onContextItemSelected(menuitem);
	}

	public void profileEditDone(RemoteProfile oldProfile,
			RemoteProfile newProfile) {
	}
}
