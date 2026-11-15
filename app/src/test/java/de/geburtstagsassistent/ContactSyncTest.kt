package de.geburtstagsassistent

import android.Manifest
import android.app.Application
import android.content.ContentProvider
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.provider.ContactsContract
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import org.robolectric.shadows.ShadowContentResolver
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk=[33])
class ContactSyncTest {
    private class FakeProvider(private val withDate:Boolean=true,private val noContacts:Boolean=false):ContentProvider(){
        override fun onCreate()=true
        override fun getType(uri:Uri):String?=null
        override fun insert(uri:Uri,values:ContentValues?):Uri?=null
        override fun delete(uri:Uri,selection:String?,args:Array<out String>?):Int=0
        override fun update(uri:Uri,values:ContentValues?,selection:String?,args:Array<out String>?):Int=0
        override fun query(uri:Uri,projection:Array<out String>?,selection:String?,
            selectionArgs:Array<out String>?,sortOrder:String?):Cursor?{
            if(noContacts)return null
            val columns=projection?:emptyArray()
            return MatrixCursor(columns).apply{
                when(uri.path){
                    "/contacts" -> addRow(arrayOf<Any>(123L,"lookup-anna","Anna Muster"))
                    "/data" -> {
                        addRow(arrayOf<Any>(123L,ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE,
                            "+49123456789",2))
                        if(withDate){
                            addRow(arrayOf<Any>(123L,ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                                "1990-05-12",ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY))
                            addRow(arrayOf<Any>(123L,ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE,
                                "2015-07-10",ContactsContract.CommonDataKinds.Event.TYPE_ANNIVERSARY))
                        }
                    }
                }
            }
        }
    }

    private fun context():Application{
        val app=ApplicationProvider.getApplicationContext<Application>()
        shadowOf(app).grantPermissions(Manifest.permission.READ_CONTACTS)
        return app
    }

    @Test fun importsContactsWithBirthdayAndWeddingAndDoesNotDuplicateOnRepeat(){
        val app=context()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,FakeProvider())
        Store(app).use { db->
            val first=Contacts.sync(app,db)
            assertEquals(1,first.scanned)
            assertEquals(1,first.added)
            assertEquals(1,first.birthdays)
            assertEquals(1,first.anniversaries)
            assertEquals(1,db.people().size)
            assertEquals("1990-05-12",db.people()[0].birthday)
            assertEquals("+49123456789",db.people()[0].phone)
            assertEquals(2,db.events().size)
            val second=Contacts.sync(app,db)
            assertEquals(0,second.added)
            assertEquals(1,second.updated)
            assertEquals(1,db.people().size)
            assertEquals(2,db.events().size)
        }
    }

    @Test fun importsContactsWithoutBirthdayForMissingList(){
        val app=context()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,FakeProvider(withDate=false))
        Store(app).use{db->
            val result=Contacts.sync(app,db)
            assertEquals(1,result.scanned)
            assertEquals(0,result.birthdays)
            assertEquals(1,db.people().count{it.birthday==null})
        }
    }

    @Test fun sourceErrorsAreNotReportedAsZeroNewContacts(){
        val app=context()
        ShadowContentResolver.registerProviderInternal(ContactsContract.AUTHORITY,FakeProvider(noContacts=true))
        Store(app).use{db->
            try{
                Contacts.sync(app,db)
                fail("Provider-Fehler muss sichtbar sein")
            }catch(expected:IllegalStateException){
                assertTrue(expected.message!!.contains("Providerantwort"))
            }
        }
    }

    @Test fun parsesGoogleBirthdayWithoutYear(){
        assertEquals("--05-12",Dates.contactDate("--05-12"))
        assertEquals("1990-05-12",Dates.contactDate("1990-05-12"))
        assertNull(Dates.contactDate("unbekannt"))
    }

    @Test fun allSaintsSchoolBreakFollowup(){
        assertEquals(LocalDate.of(2026,11,9),
            Dates.followup(LocalDate.of(2026,11,3),emptySet()))
    }

    @Test fun february29ShowsOnFebruary28InNonLeapYear(){
        assertEquals(LocalDate.of(2027,2,28),Dates.occurrence("--02-29",2027))
    }
}
