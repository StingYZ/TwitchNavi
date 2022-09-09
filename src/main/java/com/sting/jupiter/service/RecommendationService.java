package com.sting.jupiter.service;

import com.sting.jupiter.dao.FavoriteDao;
import com.sting.jupiter.entity.db.Item;
import com.sting.jupiter.entity.db.ItemType;
import com.sting.jupiter.entity.response.Game;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RecommendationService {
    private static final int DEFAULT_GAME_LIMIT = 3;
    private static final int DEFAULT_PER_GAME_RECOMMENDATION_LIMIT = 10;
    private static final int DEFAULT_TOTAL_RECOMMENDATION_LIMIT = 20;

    @Autowired
    private FavoriteDao favoriteDao;

    @Autowired
    private GameService gameService;

    public Map<String, List<Item>> recommendItemsByDefault() throws RecommendationException{
        Map<String,List<Item>> recommendItemMap = new HashMap<>();

        List<Game> topGames;
        try{
            topGames=gameService.topGames(DEFAULT_GAME_LIMIT);
        }catch(TwitchException ex){
            throw new RecommendationException("Failed to get game data for recommendation.");
        }

        for(ItemType type: ItemType.values()){
            recommendItemMap.put(type.toString(),recommendByTopGames(type,topGames));
        }
        return recommendItemMap;
    }

    private List<Item> recommendByTopGames(ItemType type, List<Game> topGames) throws RecommendationException {
        List<Item> recommendedItems = new ArrayList<>();
        for (Game game : topGames) {
            List<Item> items;
            try {
                items = gameService.searchByType(game.getId(), type, DEFAULT_PER_GAME_RECOMMENDATION_LIMIT);
            } catch (TwitchException e) {
                throw new RecommendationException("Failed to get recommendation result");
            }
            for (Item item : items) {
                if (recommendedItems.size() == DEFAULT_TOTAL_RECOMMENDATION_LIMIT) {
                    return recommendedItems;
                }
                recommendedItems.add(item);
            }
        }
        return recommendedItems;
    }

    public Map<String, List<Item>> recommendItemsByUser(String userId) throws RecommendationException{
        Map<String,List<Item>> recommendItemMap = new HashMap<>();

        Set<String> favoriteItemIds;

        Map<String,List<String>> favoriteGameIds;

        favoriteItemIds = favoriteDao.getFavoriteItemIds(userId);
        favoriteGameIds = favoriteDao.getFavoriteGameIds(favoriteItemIds);

        for(Map.Entry<String,List<String>> entry:favoriteGameIds.entrySet()){
            if(entry.getValue().size()==3){
                List<Game> topGames;
                try{
                    topGames = gameService.topGames(DEFAULT_GAME_LIMIT);
                }catch (TwitchException ex){
                    throw new RecommendationException("Failed to get game data for recommendation.");
                }
                recommendItemMap.put(entry.getKey(),recommendByTopGames(ItemType.valueOf(entry.getKey()),topGames));
            }else{
                recommendItemMap.put(entry.getKey(),recommendByFavoriteHistory(favoriteItemIds,entry.getValue(),ItemType.valueOf(entry.getKey())));
            }

        }
        return recommendItemMap;
    }

    public List<Item> recommendByFavoriteHistory(Set<String> favoriteItemIds, List<String> favoriteGameIds, ItemType type){
        // Count the favorite game IDs from the database for the given user. E.g. if the favorited game ID list is ["1234", "2345", "2345", "3456"], the returned Map is {"1234": 1, "2345": 2, "3456": 1}
        Map<String, Long> favoriteGameIdByCount = new HashMap<>();
        for(String gameId : favoriteGameIds) {
          favoriteGameIdByCount.put(gameId, favoriteGameIdByCount.getOrDefault(gameId, 0L) + 1);
        }

//        Map<String, Long> favoriteGameIdByCount = favoriteGameIds.parallelStream()
//                .collect(Collectors.groupingBy(Function.identity(), Collectors.counting()));


        // Sort the game Id by count. E.g. if the input is {"1234": 1, "2345": 2, "3456": 1}, the returned Map is {"2345": 2, "1234": 1, "3456": 1}
        List<Map.Entry<String, Long>> sortedFavoriteGameIdListByCount = new ArrayList<>(
                favoriteGameIdByCount.entrySet());
        sortedFavoriteGameIdListByCount.sort((Map.Entry<String, Long> e1, Map.Entry<String, Long> e2) -> Long
                .compare(e2.getValue(), e1.getValue()));
        // See also: https://stackoverflow.com/questions/109383/sort-a-mapkey-value-by-values


        if (sortedFavoriteGameIdListByCount.size() > DEFAULT_GAME_LIMIT) {
            sortedFavoriteGameIdListByCount = sortedFavoriteGameIdListByCount.subList(0, DEFAULT_GAME_LIMIT);
        }


        List<Item> recommendedItems = new ArrayList<>();


        // Search Twitch based on the favorite game IDs returned in the last step.

        for (Map.Entry<String, Long> favoriteGame : sortedFavoriteGameIdListByCount) {
            List<Item> items;
            try {
                items = gameService.searchByType(favoriteGame.getKey(), type, DEFAULT_PER_GAME_RECOMMENDATION_LIMIT);
            } catch (TwitchException e) {
                throw new RecommendationException("Failed to get recommendation result");
            }


            for (Item item : items) {
                if (recommendedItems.size() == DEFAULT_TOTAL_RECOMMENDATION_LIMIT) {
                    return recommendedItems;
                }
                if (!favoriteItemIds.contains(item.getId())) {
                    recommendedItems.add(item);
                }
            }
        }

        return recommendedItems;
    }
}

