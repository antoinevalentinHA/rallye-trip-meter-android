package fr.arsenal.rallyetripmeter.domain.diag

/*
 * ARSENAL RALLYE — Tick log sink contract
 *
 * Rôle :
 * - Définit le contrat d'un puits de journal d'observabilité des ticks GPS.
 *
 * Contraintes :
 * - Contrat uniquement.
 * - Aucun lien avec l'UI, Android, ni le système de fichiers.
 * - Aucune décision métier : le puits reçoit, il n'interprète pas.
 *
 * Principe :
 * - logMeta est appelé au plus une fois, en tête de session.
 * - log est appelé une fois par tick.
 * - flush garantit la durabilité de ce qui a été journalisé ; l'implémentation
 *   décide de sa stratégie de tampon (le chemin chaud ne doit jamais bloquer).
 * - L'implémentation par défaut est un no-op : sans câblage explicite,
 *   l'observabilité ne coûte rien.
 */
interface TickLogSink {
    fun logMeta(meta: TickLogMeta)

    fun log(entry: TickLogEntry)

    fun flush()
}

/*
 * Implémentation neutre : aucun effet, aucun état.
 */
class NoOpTickLogSink : TickLogSink {
    override fun logMeta(meta: TickLogMeta) {
        // No-op volontaire.
    }

    override fun log(entry: TickLogEntry) {
        // No-op volontaire.
    }

    override fun flush() {
        // No-op volontaire.
    }
}
