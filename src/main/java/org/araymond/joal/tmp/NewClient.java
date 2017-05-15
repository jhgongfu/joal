package org.araymond.joal.tmp;

import com.google.common.collect.Lists;
import com.turn.ttorrent.common.Peer;
import com.turn.ttorrent.common.protocol.TrackerMessage.AnnounceRequestMessage.RequestEvent;
import org.araymond.joal.core.client.emulated.BitTorrentClient;
import org.araymond.joal.core.config.JoalConfigProvider;
import org.araymond.joal.core.events.NoMoreLeechers;
import org.araymond.joal.core.events.NoMoreTorrentsFileAvailable;
import org.araymond.joal.core.exception.NoMoreTorrentsFileAvailableException;
import org.araymond.joal.core.torrent.watcher.TorrentFileProvider;
import org.araymond.joal.core.ttorent.client.MockedTorrent;
import org.araymond.joal.core.ttorent.client.announce.Announcer;
import org.araymond.joal.core.ttorent.client.announce.AnnouncerEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by raymo on 14/05/2017.
 */
public class NewClient implements AnnouncerEventListener {
    private static final Logger logger = LoggerFactory.getLogger(NewClient.class);

    private final JoalConfigProvider configProvider;
    private final TorrentFileProvider torrentFileProvider;
    private final ApplicationEventPublisher publisher;
    private final BitTorrentClient bitTorrentClient;
    private final List<Announcer> announcers;
    private final Peer self;

    public NewClient(final Peer peer, final JoalConfigProvider configProvider, final TorrentFileProvider torrentFileProvider, final ApplicationEventPublisher publisher, final BitTorrentClient bitTorrentClient) {
        this.configProvider = configProvider;
        this.torrentFileProvider = torrentFileProvider;
        this.publisher = publisher;
        this.bitTorrentClient = bitTorrentClient;
        this.announcers = new ArrayList<>();

        this.self = peer;
    }

    public void share() {
        // TODO : number of torrent to seed should be in config
        final int numberOfTorrentToSeed = 1;
        for (int i = 0; i < numberOfTorrentToSeed; ++i) {
            try {
                final Announcer announcer = addSeedingTorrent();

            } catch (final NoMoreTorrentsFileAvailableException e) {
                if (this.announcers.isEmpty()) {
                    this.publisher.publishEvent(new NoMoreTorrentsFileAvailable());
                }
                return;
            }
        }
    }

    private Announcer addSeedingTorrent() throws NoMoreTorrentsFileAvailableException {
        //TODO : replace getRandom by something else to ensure we are not seeding the same torrent twice
        final MockedTorrent torrent = torrentFileProvider.getRandomTorrentFile();

        final Announcer announcer = new Announcer(torrent, this.self, this.bitTorrentClient, publisher);
        announcer.registerEventListener(this);
        logger.debug("Added announcer for Torrent {}", torrent.getName());
        this.announcers.add(announcer);
        announcer.start();
        return announcer;
    }

    public void stop() {
        // We need to work with a copy of the list, because when stop, an event is launched that remove the announcer from the list.
        // And it result in a concurrent modification exception.
        Lists.newArrayList(this.announcers).forEach(Announcer::stop);
    }

    @Override
    public void onAnnounceRequesting(final RequestEvent event, final long uploaded, final long downloaded, final long left) {
        // TODO : log announce
    }

    @Override
    public void onNoMoreLeecherForTorrent(final Announcer announcer, final MockedTorrent torrent) {
        this.torrentFileProvider.moveToArchiveFolder(torrent);
        publisher.publishEvent(new NoMoreLeechers(torrent));
        announcer.stop();
    }

    @Override
    public void onAnnouncerStop(final Announcer announcer, final MockedTorrent torrent) {
        logger.debug("Removed announcer for Torrent {}", torrent.getName());
        this.announcers.remove(announcer);
        // TODO : restart another one
    }

}
