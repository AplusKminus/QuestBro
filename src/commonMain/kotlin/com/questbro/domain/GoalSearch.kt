package com.questbro.domain

data class SearchableGoal(
    val id: String,
    val name: String,
    val description: String,
    val targetId: String,
    val category: String,
    val searchKeywords: List<String>
)

class GoalSearch {
    
    fun createSearchableGoals(gameData: GameData): List<SearchableGoal> {
        val actionGoals = gameData.actions.values.map { action ->
            SearchableGoal(
                id = "action_${action.id}",
                name = action.name,
                description = action.description,
                targetId = action.id,
                category = action.category.name,
                searchKeywords = extractKeywords(action.name, action.description, action.category.name)
            )
        }
        
        val itemGoals = gameData.items.values.flatMap { item ->
            // Find actions that provide this item
            val providingActions = gameData.actions.values.filter { action ->
                action.rewards.any { it.itemId == item.id }
            }
            
            // Create a searchable goal for each providing action
            providingActions.map { action ->
                SearchableGoal(
                    id = "item_${item.id}_via_${action.id}",
                    name = "Obtain ${item.name}",
                    description = "Get ${item.name} by ${action.name}",
                    targetId = action.id, // Target the action, not the item
                    category = "ITEM",
                    searchKeywords = extractKeywords(item.name, item.description, action.name, "item")
                )
            }
        }
        
        return (actionGoals + itemGoals).sortedBy { it.name }
    }
    
    fun searchGoals(
        searchableGoals: List<SearchableGoal>,
        query: String,
        maxResults: Int = 20
    ): List<SearchableGoal> {
        if (query.isBlank()) {
            return searchableGoals.take(maxResults)
        }
        
        val queryTerms = query.lowercase().split(Regex("\\s+")).filter { it.isNotBlank() }
        
        return searchableGoals
            .map { goal -> goal to calculateRelevanceScore(goal, queryTerms) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second }
            .take(maxResults)
            .map { it.first }
    }
    
    private fun extractKeywords(vararg texts: String): List<String> {
        return texts.flatMap { text ->
            text.lowercase()
                .split(Regex("[^a-z0-9]+"))
                .filter { it.length > 2 } // Skip very short words
        }.distinct()
    }
    
    private fun calculateRelevanceScore(goal: SearchableGoal, queryTerms: List<String>): Int {
        var score = 0
        
        for (term in queryTerms) {
            // Exact name match gets highest score
            if (goal.name.lowercase().contains(term)) {
                score += 10
            }
            
            // Description match gets medium score
            if (goal.description.lowercase().contains(term)) {
                score += 5
            }
            
            // Category match gets lower score
            if (goal.category.lowercase().contains(term)) {
                score += 3
            }
            
            // Keyword match gets base score
            if (goal.searchKeywords.any { it.contains(term) }) {
                score += 1
            }
        }
        
        return score
    }
    
    fun createGoalFromSearchable(searchableGoal: SearchableGoal): Goal {
        return Goal(
            id = searchableGoal.id,
            targetId = searchableGoal.targetId,
            description = searchableGoal.name,
            priority = 0
        )
    }
}