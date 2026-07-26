importScripts('https://www.gstatic.com/firebasejs/9.22.0/firebase-app-compat.js');
importScripts('https://www.gstatic.com/firebasejs/9.22.0/firebase-messaging-compat.js');

firebase.initializeApp({
  apiKey: "AIzaSyDRPBFvXsQ6P7Gk_BfgVkYsjbYHXC_Ev-U",
  authDomain: "mju-sugangsincheong-helper.firebaseapp.com",
  projectId: "mju-sugangsincheong-helper",
  storageBucket: "mju-sugangsincheong-helper.firebasestorage.app",
  messagingSenderId: "570409366164",
  appId: "1:570409366164:web:ca2f6c38ca25d1661b3a81",
  measurementId: "G-J8XMFFEQKM"
});

const messaging = firebase.messaging();

messaging.onBackgroundMessage((payload) => {
  console.log('[firebase-messaging-sw.js] Received background message ', payload);
  const notificationTitle = payload.notification ? payload.notification.title : '알림';
  const notificationOptions = {
    body: payload.notification ? payload.notification.body : '',
  };

  self.registration.showNotification(notificationTitle, notificationOptions);
});
