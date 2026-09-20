package atifscodeworks.urukkumanush;

public interface GameActionListener {
    void onShowActivationDialog(Runnable onActivated);
    void onShowOptionsDialog();
    void onShowCreditsDialog();
    void onShowLeaderboardDialog();
    void onNewHighScore(int score);
}

