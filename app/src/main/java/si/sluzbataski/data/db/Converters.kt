package si.sluzbataski.data.db

import androidx.room.TypeConverter
import si.sluzbataski.data.entity.TaskEventType
import si.sluzbataski.data.entity.Urgency

/**
 * Pretvorniki za Room: enume shranjujemo kot besedilo (ime enuma), da je baza
 * berljiva in odporna na spremembo vrstnega reda v enumu.
 */
class Converters {
    @TypeConverter fun urgencyToString(u: Urgency): String = u.name
    @TypeConverter fun stringToUrgency(s: String): Urgency = Urgency.fromName(s)
    @TypeConverter fun eventTypeToString(t: TaskEventType): String = t.name
    @TypeConverter fun stringToEventType(s: String): TaskEventType = TaskEventType.fromName(s)
}
