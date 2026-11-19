<!-- status: draft | issue: #352 -->
# Group invite QR

Add **Show QR** beside **Share link** on the group invite. The code contains the existing invite
link and uses the same black-on-white QR component as UPI payments. Revoking the link revokes its QR.

Add **Scan QR** to Join group. CameraX provides a preview and ZXing decodes the QR on the phone.
The app requests camera access from the scan action and discards frames after decoding. A valid link
loads the existing group preview, which still requires the person to confirm. A different QR shows
an error with the option to scan again or use the code field.

Declaring camera access also affects Android's system camera intent. Both existing bill-camera
buttons request access when tapped and keep their gallery options available.

On the phone, scan a QR from a second device, check the preview, and confirm the join. Try an
unrelated QR, scan again, turn down camera access, and check both bill-camera entry points.
