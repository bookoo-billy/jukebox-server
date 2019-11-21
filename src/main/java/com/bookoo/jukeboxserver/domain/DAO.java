package com.bookoo.jukeboxserver.domain;

import java.net.URI;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.bookoo.jukeboxserver.config.Config;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component()
public class DAO {

    @Autowired
    private Config config;

    public Artist createArtist(String name) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                        .prepareStatement("INSERT INTO artists(name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name=? RETURNING *");
        pStat.setString(1, name);
        pStat.setString(2, name);

        if (pStat.execute()) {
            ResultSet rSet = pStat.getResultSet();
            if (rSet.next()) {
                return new Artist(UUID.fromString(rSet.getString("id")), rSet.getString("name"), null, null);
            }
        }

        return null;
    }

	public Album createAlbum(String name, String artistId) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                        .prepareStatement("INSERT INTO albums(name, artistid) VALUES (?, ?::uuid) ON CONFLICT (name, artistid) DO UPDATE SET name=?, artistid=?::uuid RETURNING *");
        pStat.setString(1, name);
        pStat.setString(2, artistId);
        pStat.setString(3, name);
        pStat.setString(4, artistId);

        if (pStat.execute()) {
            ResultSet rSet = pStat.getResultSet();
            if (rSet.next()) {
                return new Album(UUID.fromString(rSet.getString("id")), rSet.getString("name"), null, null);
            }
        }

        return null;
	}

    public Album createAlbum(String name, Artist artist) throws SQLException {
        return createAlbum(name, artist.getId().toString());
    }

	public Song createSong(String name, String artistId, String albumId, Integer track, URI uri) throws SQLException {
        PreparedStatement pStatSongs = config.dbConnection()
                                                .prepareStatement("INSERT INTO songs(name, artistid, albumid, track, uri) VALUES (?, ?::uuid, ?::uuid, ?, ?) ON CONFLICT (name, artistid, albumid) DO UPDATE SET name=?, artistid=?::uuid, albumid=?::uuid, track=?, uri=? RETURNING *");

        pStatSongs.setString(1, name);
        pStatSongs.setString(2, artistId);
        pStatSongs.setString(3, albumId);

        if (track == null) {
            pStatSongs.setNull(4, Types.INTEGER);
        } else {
            pStatSongs.setInt(4, track);
        }

        pStatSongs.setString(5, uri.toString());

        pStatSongs.setString(6, name);
        pStatSongs.setString(7, artistId);
        pStatSongs.setString(8, albumId);

        if (track == null) {
            pStatSongs.setNull(9, Types.INTEGER);
        } else {
            pStatSongs.setInt(9, track);
        }

        pStatSongs.setString(10, uri.toString());

        if (pStatSongs.execute()) {
            ResultSet rSet = pStatSongs.getResultSet();
            if (rSet.next()) {
                PreparedStatement pStatAlbumSongs = config.dbConnection()
                                    .prepareStatement("INSERT INTO albumsongs(albumid, songid) VALUES (?::uuid, ?::uuid) ON CONFLICT DO NOTHING RETURNING *");
                pStatAlbumSongs.setString(1, albumId);
                pStatAlbumSongs.setString(2, rSet.getString("id"));

                if (pStatAlbumSongs.execute()) {
                    return new Song(
                        UUID.fromString(rSet.getString("id")),
                        rSet.getString("name"),
                        new Album(UUID.fromString(albumId), null, null, null),
                        new Artist(UUID.fromString(artistId), null, null, null),
                        rSet.getInt("track"),
                        URI.create(rSet.getString("uri"))
                    );
                }
            }
        }

        return null;
    }

    public Song createSong(String name, Artist artist, Album album, Integer track, URI uri) throws SQLException {
        return createSong(name, artist.getId().toString(), album.getId().toString(), track, uri);
    }

	public Playlist createPlaylist(String name) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                        .prepareStatement("INSERT INTO playlists(name) VALUES (?) ON CONFLICT (name) DO UPDATE SET name=? RETURNING *");
        pStat.setString(1, name);
        pStat.setString(2, name);

        if (pStat.execute()) {
            ResultSet rSet = pStat.getResultSet();
            if (rSet.next()) {
                return new Playlist(UUID.fromString(rSet.getString("id")), rSet.getString("name"), null);
            }
        }

        return null;
    }

	public Playlist addSongToPlaylist(String playlistId, String songId) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                            .prepareStatement("INSERT INTO playlistsongs(playlistid, songid) VALUES(?::uuid, ?::uuid) ON CONFLICT (playlistid, songid, inserttime) DO UPDATE SET playlistid=?::uuid, songid=?::uuid RETURNING *");

        pStat.setString(1, playlistId);
        pStat.setString(2, songId);
        pStat.setString(3, playlistId);
        pStat.setString(4, songId);

        if (pStat.execute()) {
            ResultSet rSet = pStat.getResultSet();
            if (rSet.next()) {
                return new Playlist(UUID.fromString(rSet.getString("playlistid")), null, null);
            }
        }

        return null;
	}

	public Playlist removeSongFromPlaylist(String playlistId, String songId, String timestamp) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                            .prepareStatement("DELETE FROM playlistsongs WHERE playlistid=?::uuid AND songid=?::uuid AND inserttime=? RETURNING *");

        pStat.setString(1, playlistId);
        pStat.setString(2, songId);
        pStat.setObject(3, LocalDateTime.parse(timestamp));

        if (pStat.execute()) {
            ResultSet rSet = pStat.getResultSet();
            if (rSet.next()) {
                return new Playlist(UUID.fromString(playlistId), null, null);
            }
        }

        return null;
    }

    public Playlist getPlaylist(String playlistId) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                        .prepareStatement("SELECT * FROM playlists WHERE id::text=?");
        pStat.setString(1, playlistId);

        ResultSet rSet = pStat.executeQuery();
        if (rSet.next()) {
            return new Playlist(
                UUID.fromString(rSet.getString("id")),
                rSet.getString("name"),
                null
            );
        }

        return null;
    }

	public List<PlaylistItem> getItemsOfPlaylist(Playlist playlist) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                        .prepareStatement(
                                            "SELECT songs.id AS songid, songs.name AS songname, songs.track AS songtrack, songs.uri AS songuri, playlistsongs.inserttime AS psinserttime " +
                                            "FROM playlists, playlistsongs, songs " +
                                            "WHERE playlists.id=?::uuid AND playlistsongs.playlistid=playlists.id AND songs.id = playlistsongs.songid " +
                                            "ORDER BY inserttime ASC");
        pStat.setString(1, playlist.getId().toString());

        ResultSet rSet = pStat.executeQuery();
        List<PlaylistItem> playlistItems = new ArrayList<> ();

        while (rSet.next()) {
            playlistItems.add(
                new PlaylistItem(
                    new Song(
                        UUID.fromString(rSet.getString("songid")),
                        rSet.getString("songname"),
                        null,
                        null,
                        rSet.getInt("songtrack"),
                        URI.create(rSet.getString("songuri"))
                    ),
                    rSet.getObject("psinserttime", LocalDateTime.class)
                )
            );
        }

        return playlistItems;
	}

	public String getNameOfPlaylist(Playlist playlist) throws SQLException {
        PreparedStatement pStat = config.dbConnection()
                                        .prepareStatement("SELECT name FROM playlists WHERE id=?::uuid");
        pStat.setString(1, playlist.getId().toString());

        ResultSet rSet = pStat.executeQuery();

        if (rSet.next()) {
            return rSet.getString("name");
        }

        return null;
	}
}