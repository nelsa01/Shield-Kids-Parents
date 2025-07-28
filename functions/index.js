const functions = require("firebase-functions");
const admin = require("firebase-admin");
admin.initializeApp();

exports.beforeCreateChild = functions.firestore
    .document("children/{childId}")
    .onCreate(async (snap, context) => {
      const pin = snap.data().pin;
      const samePin = await admin
          .firestore()
          .collection("children")
          .where("pin", "==", pin)
          .get();
      if (samePin.size > 1) {
        await snap.ref.delete();
        throw new functions.https.HttpsError(
            "already-exists",
            "PIN already taken",
        );
      }
    });

const crypto = require("crypto");

// Triggered on every new child document
exports.onChildCreated = functions.firestore
    .document("children/{childId}")
    .onCreate(async (snap, ctx) => {
      const data = snap.data();
      const parentUid = data.parentUid;

      // 1. Hash the PIN using SHA-256 (hex string)
      const hashedPin = crypto
          .createHash("sha256")
          .update(data.pin)
          .digest("hex");

      // 2. Replace plain PIN with hashed PIN
      await snap.ref.update({pin: hashedPin});

      // 3. Fetch parent email
      const parentDoc = await admin
          .firestore()
          .collection("users")
          .doc(parentUid)
          .get();
      const email = parentDoc.data().email;

      // 4. Send email
      await admin.firestore().collection("mail").add({
        to: email,
        message: {
          subject: "Shield-Kids – Child PIN",
          text: `Hi,

You just added "${data.name}" to Shield-Kids.
PIN (keep it safe): ${data.pin}

Regards,
Shield-Kids Team`},
      });
    });
