package fr.arsenal.rallyetripmeter.android.diag

import fr.arsenal.rallyetripmeter.domain.diag.NoOpTickLogSink
import fr.arsenal.rallyetripmeter.domain.diag.TickLogEntry
import fr.arsenal.rallyetripmeter.domain.diag.TickLogMeta
import fr.arsenal.rallyetripmeter.domain.diag.TickLogSink

/*
 * ARSENAL RALLYE — Process-wide tick log sink holder
 *
 * Rôle :
 * - Fournit le TickLogSink stable injecté à la construction du TripRuntime
 *   process-wide, et permet d'y brancher/débrancher un sink de session.
 *
 * Contraintes :
 * - Pur Kotlin : aucun import Android (pattern LocationEngineHolder).
 * - Le runtime étant construit une seule fois par process avec un sink figé,
 *   ce holder expose un délégateur permanent dont seul le délégué change.
 * - Sans session active, le délégué est no-op : l'observabilité ne coûte rien.
 *
 * Principe :
 * - getSink() est passé à TripRuntimeHolder.getRuntime par toutes les coutures
 *   de construction (ViewModelFactory, service).
 * - Le foreground service installe un sink de session au démarrage
 *   (s'il n'y en a pas déjà un) et le retire/ferme à l'arrêt.
 */
object TickLogSinkHolder {
    private val delegatingSink = DelegatingTickLogSink()

    fun getSink(): TickLogSink {
        return delegatingSink
    }

    fun hasSessionSink(): Boolean {
        return delegatingSink.delegate !is NoOpTickLogSink
    }

    fun installSessionSink(sink: FileTickLogSink) {
        delegatingSink.delegate = sink
    }

    /*
     * Retire le sink de session et restaure le no-op. Retourne le sink retiré
     * (à fermer par l'appelant), ou null si aucune session n'était active.
     */
    fun clearSessionSink(): FileTickLogSink? {
        val current = delegatingSink.delegate
        delegatingSink.delegate = NoOpTickLogSink()
        return current as? FileTickLogSink
    }

    private class DelegatingTickLogSink : TickLogSink {
        @Volatile
        var delegate: TickLogSink = NoOpTickLogSink()

        override fun logMeta(meta: TickLogMeta) {
            delegate.logMeta(meta)
        }

        override fun log(entry: TickLogEntry) {
            delegate.log(entry)
        }

        override fun flush() {
            delegate.flush()
        }
    }
}
