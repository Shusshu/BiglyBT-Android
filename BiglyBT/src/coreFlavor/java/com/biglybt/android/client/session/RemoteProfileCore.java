/*
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
 */

package com.biglybt.android.client.session;

import android.Manifest;
import android.content.SharedPreferences;

import com.biglybt.android.client.AppPreferences;
import com.biglybt.android.client.BiglyBTApp;
import com.biglybt.android.client.CorePrefs;
import com.biglybt.android.client.session.RemoteProfile;

import java.util.List;
import java.util.Map;

/**
 * Created by TuxPaper on 11/7/17.
 */

public class RemoteProfileCore
		extends RemoteProfile
{
	protected RemoteProfileCore(int remoteType) {
		super(remoteType);
	}

	protected RemoteProfileCore(Map mapRemote) {
		super(mapRemote);
	}

	protected RemoteProfileCore(String user, String ac) {
		super(user, ac);
	}

	@Override
	public List<String> getRequiredPermissions() {
		final List<String> permissionsNeeded = super.getRequiredPermissions();
		permissionsNeeded.add(Manifest.permission.WRITE_EXTERNAL_STORAGE);

		final CorePrefs corePrefs = new CorePrefs();
		if (corePrefs.getPrefAutoStart()) {
			permissionsNeeded.add(Manifest.permission.RECEIVE_BOOT_COMPLETED);
		}
		if (corePrefs.getPrefDisableSleep()) {
			permissionsNeeded.add(Manifest.permission.WAKE_LOCK);
		}

		return permissionsNeeded;
	}
}
