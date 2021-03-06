package org.musicpd.android.fragments;

import org.a0z.mpd.Album;
import org.a0z.mpd.Artist;
import org.a0z.mpd.Genre;
import org.a0z.mpd.Item;
import org.a0z.mpd.MPDCommand;
import org.a0z.mpd.Music;
import org.a0z.mpd.exception.MPDServerException;

import android.app.Activity;
import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.actionbarsherlock.internal.widget.IcsListPopupWindow;

import org.musicpd.android.InformationActivity;
import org.musicpd.android.MPDApplication;
import org.musicpd.android.R;
import org.musicpd.android.adapters.ArrayIndexerAdapter;
import org.musicpd.android.adapters.PopupMenuAdapter;
import org.musicpd.android.adapters.PopupMenuItem;
import org.musicpd.android.helpers.CoverAsyncHelper;
import org.musicpd.android.helpers.AlbumCoverDownloadListener;
import org.musicpd.android.library.ILibraryFragmentActivity;
import org.musicpd.android.tools.Log;
import org.musicpd.android.tools.RelatedSongs;
import org.musicpd.android.tools.StringResource;
import org.musicpd.android.tools.Tools;
import org.musicpd.android.views.SongDataBinder;

public class SongsFragment extends BrowseFragment {

	private static final int FALLBACK_COVER_SIZE = 80; // In DIP
	private static final String EXTRA_GENRE = "genre";
	private static final String EXTRA_ARTIST = "artist";
	private static final String EXTRA_ALBUM = "album";

	Genre genre = null;
	Album album = null;
	Artist artist = null;
	TextView headerArtist;
	TextView headerInfo;

	private AlbumCoverDownloadListener coverArtListener;
	ImageView coverArt;
	ProgressBar coverArtProgress;

	CoverAsyncHelper coverHelper;
	ImageButton albumMenu;
	IcsListPopupWindow popupMenu;

	public SongsFragment() {
		super(R.string.addSong, R.string.songAdded, MPDCommand.MPD_SEARCH_TITLE);
		showRelated = lastShowRelated;
	}

	@Override
	public void onCreate(Bundle icicle) {
		super.onCreate(icicle);
		if (icicle != null)
			init((Genre) icicle.getParcelable(EXTRA_GENRE), (Artist) icicle.getParcelable(EXTRA_ARTIST), (Album) icicle.getParcelable(EXTRA_ALBUM));
	}

	public SongsFragment init(Genre g, Artist ar, Album al) {
		genre = g;
		artist = ar;
		album = al;
		return this;
	}

	@Override
	public void onDestroyView() {
		headerArtist = null;
		headerInfo = null;
		if (coverArtListener != null)
			coverArtListener.freeCoverDrawable();
		super.onDestroyView();
	}

	@Override
	public void onDetach() {
		coverHelper = null;
		super.onDetach();
	}

	@Override
	public StringResource getTitle() {
		if (album != null) {
			return new StringResource(album.getName());
		} else {
			return new StringResource(R.string.songs);
		}
	}

	@Override
	public int getLoadingText() {
		return R.string.loadingSongs;
	}

	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
		final View view = inflater.inflate(R.layout.songs, container, false);
		list = (ListView) view.findViewById(R.id.list);
		registerForContextMenu(list);
		list.setOnItemClickListener(this);
		loadingView = view.findViewById(R.id.loadingLayout);
		loadingTextView = (TextView) view.findViewById(R.id.loadingText);
		noResultView = view.findViewById(R.id.noResultLayout);
		loadingTextView.setText(getLoadingText());

		final View headerView = inflater.inflate(R.layout.song_header, null, false);
		coverArt = (ImageView) view.findViewById(R.id.albumCover);
		if (coverArt != null) {
			headerArtist = (TextView) view.findViewById(R.id.tracks_artist);
			headerInfo = (TextView) view.findViewById(R.id.tracks_info);
			coverArtProgress = (ProgressBar) view.findViewById(R.id.albumCoverProgress);
			albumMenu = (ImageButton) view.findViewById(R.id.album_menu);
		} else {
			headerArtist = (TextView) headerView.findViewById(R.id.tracks_artist);
			headerInfo = (TextView) headerView.findViewById(R.id.tracks_info);
			coverArt = (ImageView) headerView.findViewById(R.id.albumCover);
			coverArtProgress = (ProgressBar) headerView.findViewById(R.id.albumCoverProgress);
			albumMenu = (ImageButton) headerView.findViewById(R.id.album_menu);

			final MPDApplication app = (MPDApplication) getActivity().getApplication();
			coverArtListener = new AlbumCoverDownloadListener(getActivity(), coverArt, coverArtProgress);
			coverHelper = new CoverAsyncHelper(app, PreferenceManager.getDefaultSharedPreferences(getActivity()));
			coverHelper.setCoverRetrieversFromPreferences();
			coverHelper.setCoverMaxSizeFromScreen(getActivity());
			coverHelper.setCachedCoverMaxSize(coverArt.getHeight());
			coverHelper.addCoverDownloadListener(coverArtListener);
		}
		((TextView) headerView.findViewById(R.id.separator_title)).setText(R.string.songs);
		((ListView) list).addHeaderView(headerView, null, false);

		albumMenu.setOnClickListener(new OnClickListener() {
			@Override
			public void onClick(View v) {
				popupMenu = new IcsListPopupWindow(getActivity());
				popupMenu.setAdapter(getPopupMenuAdapter(getActivity()));
				popupMenu.setModal(true);
				popupMenu.setOnItemClickListener(new OnItemClickListener() {
					@Override
					public void onItemClick(AdapterView<?> adapterView, View view, int position, long id) {
						final int action = ((PopupMenuItem) adapterView.getAdapter().getItem(position)).actionId;
						final Activity activity = getActivity();

						app.oMPDAsyncHelper.execAsync(new Runnable() {
							@Override
							public void run() {
								boolean replace = false;
								boolean play = false;
								switch (action) {
									case SHOW_RELATED:
										lastShowRelated = showRelated ^= true;
										UpdateList(false);
										return;
									case ADDNREPLACEPLAY:
										replace = true;
										play = true;
										break;
									case ADDNREPLACE:
										replace = true;
										break;
									case ADDNPLAY:
										play = true;
										break;
									case INFO:
										InformationActivity.start(getActivity(), new String[] { "artist", artist.getName(), "album", album.getName() });
										return;
								}
								try {
									app.oMPDAsyncHelper.oMPD.add(genre, artist, album, replace, play);
									Tools.notifyUser(String.format(getResources().getString(R.string.albumAdded), album), activity);
								} catch (Exception e) {
									Log.w(e);
								}
							}
						});

						popupMenu.dismiss();
					}
				});

				final DisplayMetrics displaymetrics = new DisplayMetrics();
				getActivity().getWindowManager().getDefaultDisplay().getMetrics(displaymetrics);
				popupMenu.setContentWidth(displaymetrics.widthPixels / 2);
				popupMenu.setAnchorView(v);
				popupMenu.show();
			}
		});

		return view;
	}

	@Override
	public void onSaveInstanceState(Bundle outState) {
		outState.putParcelable(EXTRA_GENRE, genre);
		outState.putParcelable(EXTRA_ALBUM, album);
		outState.putParcelable(EXTRA_ARTIST, artist);
		super.onSaveInstanceState(outState);
	}

	@Override
	public void onItemClick(AdapterView adapterView, View v, final int position, long id) {
		final Music item = (Music) adapterView.getAdapter().getItem(position);
		if (item.getSelected())
			app.oMPDAsyncHelper.execAsync(new Runnable() {
				@Override
				public void run() {
					add(item, false, false);
				}
			});
		else
			NavigateTo(item);
	}
	
	public void NavigateTo(Music item)
	{
		Artist artist = new Artist(item.getArtist(), 1);
		Album album = new Album(item.getAlbum());
		((ILibraryFragmentActivity) getActivity()).pushLibraryFragment(new SongsFragment().init(genre, artist, album),
				"songs");
	}

	@Override
	protected String[] info(Item item) {
		return item instanceof Music
			? new String[] { "file", ((Music)item).getFullpath() } 
			: null
		;
	}

	@Override
	protected void add(Item item, boolean replace, boolean play) {
		Music music = (Music) item;
		try {
			app.oMPDAsyncHelper.oMPD.add(music, replace, play);
			Tools.notifyUser(String.format(getResources().getString(R.string.songAdded, music.getTitle()), music.getName()),
					getActivity());
		} catch (Exception e) {
			Log.w(e);
		}
	}

	@Override
	protected void add(Item item, String playlist) {
		try {
			app.oMPDAsyncHelper.oMPD.addToPlaylist(playlist, (Music) item);
			Tools.notifyUser(String.format(getResources().getString(irAdded), item), getActivity());
		} catch (Exception e) {
			Log.w(e);
		}
	}

	java.util.List<Music> songs, related;
	
	@Override
	public void asyncUpdate() {
		try {
			if (getActivity() == null)
				return;
			if (songs == null)
				songs = app.oMPDAsyncHelper.oMPD.getSongs(genre, artist, album);
			if (showRelated && related == null)
				related = RelatedSongs.items(app.oMPDAsyncHelper.oMPD, songs);
			items = showRelated? related : songs;
		} catch (MPDServerException e) {
			Log.w(e);
		}
	}

	@Override
	public void updateFromItems() {
		super.updateFromItems();
		if (items != null) {
			String artistName = null;
			try {
				artistName = getArtistForTrackList();
				headerArtist.setText(artistName);
			} catch (Exception e) {
				Log.w(e);
			}
			try {
				headerInfo.setText(getHeaderInfoString());
			} catch (Exception e) {
				Log.w(e);
			}
			try {
				if (coverHelper != null) {
					String filename = null;
					String path = null;
					for (Item item : items)
						try {
							Music song = (Music) item;
							filename = song.getFilename();
							path = song.getPath();
							artistName = song.getArtist();
							break;
						} catch (Exception e) {
							Log.w(e);
						}
					coverArtProgress.setVisibility(ProgressBar.VISIBLE);
					coverHelper.downloadCover(artistName, album.getName(), path, filename);
				} else {
					coverArtListener.onCoverNotFound();
				}
			} catch (Exception e) {
				Log.w(e);
			}
		}

	}

	@Override
	protected ListAdapter getCustomListAdapter() {
		if (items != null) {
			Music song;
			boolean differentArtists = false;
			String lastArtist = null;
			for (Item item : items) {
				song = (Music) item;
				if (lastArtist == null) {
					lastArtist = song.getArtist();
					continue;
				}
				if (!lastArtist.equalsIgnoreCase(song.getArtist())) {
					differentArtists = true;
					break;
				}
			}
			return new ArrayIndexerAdapter(getActivity(), new SongDataBinder(differentArtists, showRelated), items);
		}
		return super.getCustomListAdapter();
	}

	private String getArtistForTrackList() {
		Music song;
		String lastArtist = null;
		boolean differentArtists = false;
		for (Item item : items) {
			song = (Music) item;
			if (lastArtist == null) {
				lastArtist = song.getAlbumArtist();
				continue;
			}
			if (!lastArtist.equalsIgnoreCase(song.getAlbumArtist())) {
				differentArtists = true;
				break;
			}
		}
		if (differentArtists || lastArtist == null || lastArtist.equals("")) {
			differentArtists = false;
			for (Item item : items) {
				song = (Music) item;
				if (lastArtist == null) {
					lastArtist = song.getArtist();
					continue;
				}
				if (!lastArtist.equalsIgnoreCase(song.getArtist())) {
					differentArtists = true;
					break;
				}
			}
			if (differentArtists || lastArtist == null || lastArtist.equals("")) {
				return getString(R.string.variousArtists);
			}
			return lastArtist;
		}
		return lastArtist;
	}

	private String getTotalTimeForTrackList() {
		Music song;
		long totalTime = 0;
		for (Item item : items) {
			song = (Music) item;
			if (song.getTime() > 0)
				totalTime += song.getTime();
		}
		return Music.timeToString(totalTime);
	}

	private String getHeaderInfoString() {
		final int count = items.size();
		return String.format(getString(count > 1 ? R.string.tracksInfoHeaderPlural : R.string.tracksInfoHeader), count,
				getTotalTimeForTrackList());
	}

	@Override
	protected boolean forceEmptyView() {
		return true;
	}
	
	static boolean lastShowRelated = false;
	boolean showRelated;

	/**
	 * Popup button methods and classes
	 */

	private PopupMenuAdapter getPopupMenuAdapter(Context context) {
		return new PopupMenuAdapter(context,
				Build.VERSION.SDK_INT >= 14
					? android.R.layout.simple_spinner_dropdown_item
					: R.layout.sherlock_spinner_dropdown_item,
				new PopupMenuItem[] {
					new PopupMenuItem(SHOW_RELATED, showRelated? R.string.hideRelated : R.string.showRelated),
					new PopupMenuItem(INFO, R.string.information),
					new PopupMenuItem(ADD, R.string.addAlbum),
					new PopupMenuItem(ADDNREPLACE, R.string.addAndReplace),
					new PopupMenuItem(ADDNREPLACEPLAY, R.string.addAndReplacePlay),
					new PopupMenuItem(ADDNPLAY, R.string.addAndPlay),
				});
	}

}
