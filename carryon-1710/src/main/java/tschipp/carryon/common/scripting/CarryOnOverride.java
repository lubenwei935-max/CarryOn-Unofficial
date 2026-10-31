package tschipp.carryon.common.scripting;

import net.minecraft.nbt.NBTTagCompound;

public class CarryOnOverride
{
	// BLOCKS
	private NBTTagCompound typeBlockTag;
	private String typeMeta;
	private String typeNameBlock;
	private String typeMaterial;
	private String typeHardness;
	private String typeResistance;

	// ENTITIES
	private NBTTagCompound typeEntityTag;
	private String typeNameEntity;
	private String typeHeight;
	private String typeWidth;
	private String typeHealth;

	// CONDITIONS
	private String conditionAchievement;
	private String conditionXp;
	private String conditionGamemode;
	private String conditionScoreboard;
	private String conditionPosition;
	private String conditionEffects;

	private boolean isBlock;
	private boolean isEntity;
	private final String path;
	

	public CarryOnOverride(String path)
	{
		this.path = path;
	}
	
	public String getConditionEffects()
	{
		return conditionEffects;
	}

	public void setConditionEffects(String conditionEffects)
	{
		this.conditionEffects = conditionEffects;
	}

	@Override
	public int hashCode()
	{
		final int prime = 31;
		int result = 1;
		result = prime * result + ((path == null) ? 0 : path.hashCode());
		return result;
	}
	
	@Override
	public String toString()
	{
		return "Code: " + this.hashCode();
	}

	public boolean isBlock()
	{
		return isBlock;
	}

	public void setBlock(boolean isBlock)
	{
		this.isBlock = isBlock;
	}

	public boolean isEntity()
	{
		return isEntity;
	}

	public void setEntity(boolean isEntity)
	{
		this.isEntity = isEntity;
	}

	public NBTTagCompound getTypeBlockTag()
	{
		return typeBlockTag;
	}

	public String getTypeMeta()
	{
		return typeMeta;
	}

	public String getTypeNameBlock()
	{
		return typeNameBlock;
	}

	public String getTypeMaterial()
	{
		return typeMaterial;
	}

	public String getTypeHardness()
	{
		return typeHardness;
	}

	public String getTypeResistance()
	{
		return typeResistance;
	}

	public NBTTagCompound getTypeEntityTag()
	{
		return typeEntityTag;
	}

	public String getTypeNameEntity()
	{
		return typeNameEntity;
	}

	public String getTypeHeight()
	{
		return typeHeight;
	}

	public String getTypeWidth()
	{
		return typeWidth;
	}

	public String getTypeHealth()
	{
		return typeHealth;
	}

	public String getConditionAchievement()
	{
		return conditionAchievement;
	}

	public String getConditionXp()
	{
		return conditionXp;
	}

	public String getConditionGamemode()
	{
		return conditionGamemode;
	}

	public String getConditionScoreboard()
	{
		return conditionScoreboard;
	}

	public String getConditionPosition()
	{
		return conditionPosition;
	}

	public void setTypeBlockTag(NBTTagCompound typeBlockTag)
	{
		this.typeBlockTag = typeBlockTag;
	}

	public void setTypeMeta(String typeMeta)
	{
		this.typeMeta = typeMeta;
	}

	public void setTypeNameBlock(String typeNameBlock)
	{
		this.typeNameBlock = typeNameBlock;
	}

	public void setTypeMaterial(String typeMaterial)
	{
		this.typeMaterial = typeMaterial;
	}

	public void setTypeHardness(String typeHardness)
	{
		this.typeHardness = typeHardness;
	}

	public void setTypeResistance(String typeResistance)
	{
		this.typeResistance = typeResistance;
	}

	public void setTypeEntityTag(NBTTagCompound typeEntityTag)
	{
		this.typeEntityTag = typeEntityTag;
	}

	public void setTypeNameEntity(String typeNameEntity)
	{
		this.typeNameEntity = typeNameEntity;
	}

	public void setTypeHeight(String typeHeight)
	{
		this.typeHeight = typeHeight;
	}

	public void setTypeWidth(String typeWidth)
	{
		this.typeWidth = typeWidth;
	}

	public void setTypeHealth(String typeHealth)
	{
		this.typeHealth = typeHealth;
	}

	public void setConditionAchievement(String conditionAchievement)
	{
		this.conditionAchievement = conditionAchievement;
	}

	public void setConditionXp(String conditionXp)
	{
		this.conditionXp = conditionXp;
	}

	public void setConditionGamemode(String conditionGamemode)
	{
		this.conditionGamemode = conditionGamemode;
	}

	public void setConditionScoreboard(String conditionScoreboard)
	{
		this.conditionScoreboard = conditionScoreboard;
	}

	public void setConditionPosition(String conditionPosition)
	{
		this.conditionPosition = conditionPosition;
	}

}
