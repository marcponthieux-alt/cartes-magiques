# Cartes magiques – jeu des multiplications

Application Android pour réviser les tables de multiplication de 1 à 10, à partir des
4 carrés de 25 cartes (recto : résultat, verso : multiplications).

## Installer sur le téléphone (Xiaomi Mi 9)

1. Sur le téléphone, ouvrir https://github.com/marcponthieux-alt/cartes-magiques/releases/latest/download/cartes-magiques.apk
2. Ouvrir le fichier. Android demande d'autoriser l'installation d'applications inconnues
   pour le navigateur ou le gestionnaire de fichiers : accepter, puis **Installer**.
   (Sur MIUI, désactiver si besoin « Analyse de sécurité » pour ce fichier.)
3. Au premier lancement, **autoriser le micro**.
4. Vérifier une fois, avec le wifi, que la voix française est installée :
   Paramètres › Paramètres supplémentaires › Langue et saisie › Synthèse vocale ›
   moteur Google › Installer les données vocales › Français (France).

Ensuite tout fonctionne **sans connexion** : la voix (synthèse Android) et le micro
(reconnaissance Vosk, modèle français inclus dans l'application).

Les mises à jour s'installent par-dessus l'ancienne version (même clé de signature).

## Le jeu

- Accueil : la table 10 × 10. Toucher une case ouvre le carré de 25 cartes correspondant.
- Mémorisation : après 5 s, « Quand tu es prêt, retourne les cartes ». Double-touche ou frotter l'écran.
- Niveaux : Débutant (l'enfant choisit la carte), Avancé (le jeu impose l'ordre),
  Expert (une seule carte visible à la fois).
- Sens : partir du résultat (dire « 3 fois 8 ») ou de la multiplication (dire « 24 »).
- Bonne réponse : « Bravo », la carte reste retournée avec son autocollant.
- Mauvaise réponse : la bonne réponse s'affiche et est lue, puis la carte revient sur sa face question.

## Développement

Kotlin + Jetpack Compose. L'APK est construit par GitHub Actions
(`.github/workflows/build.yml`), qui télécharge aussi le modèle vocal Vosk.
