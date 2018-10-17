package logisticspipes.blocks.crafting;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.inventory.InventoryCraftResult;
import net.minecraft.item.ItemStack;
import net.minecraft.item.crafting.IRecipe;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.EnumFacing;
import net.minecraft.util.text.ITextComponent;

import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.items.CapabilityItemHandler;
import net.minecraftforge.items.wrapper.InvWrapper;

import logisticspipes.blocks.LogisticsSolidBlock;
import logisticspipes.blocks.LogisticsSolidTileEntity;
import logisticspipes.interfaces.IGuiOpenControler;
import logisticspipes.interfaces.IGuiTileEntity;
import logisticspipes.network.NewGuiHandler;
import logisticspipes.network.PacketHandler;
import logisticspipes.network.abstractguis.CoordinatesGuiProvider;
import logisticspipes.network.guis.block.AutoCraftingGui;
import logisticspipes.network.packets.block.CraftingSetType;
import logisticspipes.proxy.MainProxy;
import logisticspipes.request.resources.DictResource;
import logisticspipes.utils.CraftingUtil;
import logisticspipes.utils.ISimpleInventoryEventHandler;
import logisticspipes.utils.PlayerCollectionList;
import logisticspipes.utils.PlayerIdentifier;
import logisticspipes.utils.item.ItemIdentifier;
import logisticspipes.utils.item.ItemIdentifierInventory;
import logisticspipes.utils.item.ItemIdentifierStack;

public class LogisticsCraftingTableTileEntity extends LogisticsSolidTileEntity implements IInventory, IGuiTileEntity, ISimpleInventoryEventHandler, IGuiOpenControler {

	public ItemIdentifierInventory inv = new ItemIdentifierInventory(18, "Crafting Resources", 64);
	public ItemIdentifierInventory matrix = new ItemIdentifierInventory(9, "Crafting Matrix", 1);
	public ItemIdentifierInventory resultInv = new ItemIdentifierInventory(1, "Crafting Result", 1);

	private InventoryCraftResult vanillaResult = new InventoryCraftResult();

	public ItemIdentifier targetType = null;
	//just use CraftingRequirement to store flags; field "stack" is ignored
	public DictResource[] fuzzyFlags = new DictResource[9];
	public DictResource outputFuzzyFlags = new DictResource((ItemIdentifierStack) null);
	private IRecipe cache;
	private EntityPlayer fake;
	private PlayerIdentifier placedBy = null;

	private InvWrapper invWrapper = new InvWrapper(this);

	private PlayerCollectionList guiWatcher = new PlayerCollectionList();

	public LogisticsCraftingTableTileEntity() {
		matrix.addListener(this);
		for (int i = 0; i < 9; i++) {
			fuzzyFlags[i] = new DictResource((ItemIdentifierStack) null);
		}
	}

	public void cacheRecipe() {
		ItemIdentifier oldTargetType = targetType;
		cache = null;
		resultInv.clearInventorySlotContents(0);
		AutoCraftingInventory craftInv = new AutoCraftingInventory(placedBy);
		for (int i = 0; i < 9; i++) {
			craftInv.setInventorySlotContents(i, matrix.getStackInSlot(i));
		}
		List<IRecipe> list = new ArrayList<>();
		for (IRecipe r : CraftingUtil.getRecipeList()) {
			if (r.matches(craftInv, getWorld())) {
				list.add(r);
			}
		}
		if (list.size() == 1) {
			cache = list.get(0);
			resultInv.setInventorySlotContents(0, cache.getCraftingResult(craftInv));
			targetType = null;
		} else if (list.size() > 1) {
			if (targetType != null) {
				for (IRecipe recipe : list) {
					craftInv = new AutoCraftingInventory(placedBy);
					for (int i = 0; i < 9; i++) {
						craftInv.setInventorySlotContents(i, matrix.getStackInSlot(i));
					}
					ItemStack result = recipe.getCraftingResult(craftInv);
					if (!result.isEmpty() && targetType.equals(ItemIdentifier.get(result))) {
						resultInv.setInventorySlotContents(0, result);
						cache = recipe;
						break;
					}
				}
			}
			if (cache == null) {
				for (IRecipe r : list) {
					ItemStack result = r.getCraftingResult(craftInv);
					if (!result.isEmpty()) {
						cache = r;
						resultInv.setInventorySlotContents(0, result);
						targetType = ItemIdentifier.get(result);
						break;
					}
				}
			}
		} else {
			targetType = null;
		}
		outputFuzzyFlags = new DictResource(outputFuzzyFlags, resultInv.getIDStackInSlot(0));
		if (((targetType == null && oldTargetType != null) || (targetType != null && !targetType.equals(oldTargetType))) && !guiWatcher.isEmpty() && getWorld() != null && MainProxy.isServer(getWorld())) {
			MainProxy.sendToPlayerList(PacketHandler.getPacket(CraftingSetType.class).setTargetType(targetType).setTilePos(this), guiWatcher);
		}
	}

	public void cycleRecipe(boolean down) {
		cacheRecipe();
		if (targetType == null) {
			return;
		}
		cache = null;
		AutoCraftingInventory craftInv = new AutoCraftingInventory(placedBy);
		for (int i = 0; i < 9; i++) {
			craftInv.setInventorySlotContents(i, matrix.getStackInSlot(i));
		}
		List<IRecipe> list = new ArrayList<>();
		for (IRecipe r : CraftingUtil.getRecipeList()) {
			if (r.matches(craftInv, getWorld())) {
				list.add(r);
			}
		}
		if (list.size() > 1) {
			boolean found = false;
			IRecipe prev = null;
			for (IRecipe recipe : list) {
				if (found) {
					cache = recipe;
					break;
				}
				craftInv = new AutoCraftingInventory(placedBy);
				for (int i = 0; i < 9; i++) {
					craftInv.setInventorySlotContents(i, matrix.getStackInSlot(i));
				}
				if (targetType != null && targetType.equals(ItemIdentifier.get(recipe.getCraftingResult(craftInv)))) {
					if (down) {
						found = true;
					} else {
						if (prev == null) {
							cache = list.get(list.size() - 1);
						} else {
							cache = prev;
						}
						break;
					}
				}
				prev = recipe;
			}
			if (cache == null) {
				cache = list.get(0);
			}
			craftInv = new AutoCraftingInventory(placedBy);
			for (int i = 0; i < 9; i++) {
				craftInv.setInventorySlotContents(i, matrix.getStackInSlot(i));
			}
			targetType = ItemIdentifier.get(cache.getCraftingResult(craftInv));
		}
		if (!guiWatcher.isEmpty() && getWorld() != null && MainProxy.isServer(getWorld())) {
			MainProxy.sendToPlayerList(PacketHandler.getPacket(CraftingSetType.class).setTargetType(targetType).setTilePos(this), guiWatcher);
		}
		cacheRecipe();
	}

	private boolean testFuzzy(ItemIdentifier item, ItemIdentifierStack item2, int slot) {
		fuzzyFlags[slot] = new DictResource(fuzzyFlags[slot], item.makeStack(1));
		return fuzzyFlags[slot].matches(item2.getItem());
	}

	public void onBlockBreak() {
		inv.dropContents(world, getPos());
	}

	@Override
	public void InventoryChanged(IInventory inventory) {
		if (inventory == matrix) {
			cacheRecipe();
		}
	}

	public void handleNEIRecipePacket(ItemStack[] content) {
		for (int i = 0; i < 9; i++) {
			matrix.setInventorySlotContents(i, content[i]);
		}
		cacheRecipe();
	}

	@Override
	public void readFromNBT(NBTTagCompound par1nbtTagCompound) {
		super.readFromNBT(par1nbtTagCompound);
		inv.readFromNBT(par1nbtTagCompound, "inv");
		matrix.readFromNBT(par1nbtTagCompound, "matrix");
		if (par1nbtTagCompound.hasKey("placedBy")) {
			String name = par1nbtTagCompound.getString("placedBy");
			placedBy = PlayerIdentifier.convertFromUsername(name);
		} else {
			placedBy = PlayerIdentifier.readFromNBT(par1nbtTagCompound, "placedBy");
		}
		if (par1nbtTagCompound.hasKey("fuzzyFlags")) {
			NBTTagList lst = par1nbtTagCompound.getTagList("fuzzyFlags", Constants.NBT.TAG_COMPOUND);
			for (int i = 0; i < 9; i++) {
				NBTTagCompound comp = lst.getCompoundTagAt(i);
				fuzzyFlags[i].ignore_dmg = comp.getBoolean("ignore_dmg");
				fuzzyFlags[i].ignore_nbt = comp.getBoolean("ignore_nbt");
				fuzzyFlags[i].use_od = comp.getBoolean("use_od");
				fuzzyFlags[i].use_category = comp.getBoolean("use_category");
			}
		}
		if (par1nbtTagCompound.hasKey("outputFuzzyFlags")) {
			NBTTagCompound comp = par1nbtTagCompound.getCompoundTag("outputFuzzyFlags");
			outputFuzzyFlags.ignore_dmg = comp.getBoolean("ignore_dmg");
			outputFuzzyFlags.ignore_nbt = comp.getBoolean("ignore_nbt");
			outputFuzzyFlags.use_od = comp.getBoolean("use_od");
			outputFuzzyFlags.use_category = comp.getBoolean("use_category");
		}
		if (par1nbtTagCompound.hasKey("targetType")) {
			targetType = ItemIdentifier.get(new ItemStack(par1nbtTagCompound.getCompoundTag("targetType")));
		}
		cacheRecipe();
	}

	@Override
	public NBTTagCompound writeToNBT(NBTTagCompound par1nbtTagCompound) {
		par1nbtTagCompound = super.writeToNBT(par1nbtTagCompound);
		inv.writeToNBT(par1nbtTagCompound, "inv");
		matrix.writeToNBT(par1nbtTagCompound, "matrix");
		if (placedBy != null) {
			placedBy.writeToNBT(par1nbtTagCompound, "placedBy");
		}
		NBTTagList lst = new NBTTagList();
		for (int i = 0; i < 9; i++) {
			NBTTagCompound comp = new NBTTagCompound();
			comp.setBoolean("ignore_dmg", fuzzyFlags[i].ignore_dmg);
			comp.setBoolean("ignore_nbt", fuzzyFlags[i].ignore_nbt);
			comp.setBoolean("use_od", fuzzyFlags[i].use_od);
			comp.setBoolean("use_category", fuzzyFlags[i].use_category);
			lst.appendTag(comp);
		}
		par1nbtTagCompound.setTag("fuzzyFlags", lst);
		{
			NBTTagCompound comp = new NBTTagCompound();
			comp.setBoolean("ignore_dmg", outputFuzzyFlags.ignore_dmg);
			comp.setBoolean("ignore_nbt", outputFuzzyFlags.ignore_nbt);
			comp.setBoolean("use_od", outputFuzzyFlags.use_od);
			comp.setBoolean("use_category", outputFuzzyFlags.use_category);
			par1nbtTagCompound.setTag("outputFuzzyFlags", comp);
		}
		if (targetType != null) {
			NBTTagCompound type = new NBTTagCompound();
			targetType.makeNormalStack(1).writeToNBT(type);
			par1nbtTagCompound.setTag("targetType", type);
		} else {
			par1nbtTagCompound.removeTag("targetType");
		}
		return par1nbtTagCompound;
	}

	@Override
	public boolean hasCapability(Capability<?> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return true;
		}
		return super.hasCapability(capability, facing);
	}

	@Nullable
	@Override
	public <T> T getCapability(Capability<T> capability, @Nullable EnumFacing facing) {
		if (capability == CapabilityItemHandler.ITEM_HANDLER_CAPABILITY) {
			return (T) invWrapper;
		}
		return super.getCapability(capability, facing);
	}

	@Override
	public int getSizeInventory() {
		return inv.getSizeInventory();
	}

	@Override
	public boolean isEmpty() {
		return inv.isEmpty();
	}

	@Override
	public ItemStack getStackInSlot(int i) {
		return inv.getStackInSlot(i);
	}

	@Override
	public ItemStack decrStackSize(int i, int j) {
		return inv.decrStackSize(i, j);
	}

	@Override
	public ItemStack removeStackFromSlot(int i) {
		return inv.removeStackFromSlot(i);
	}

	@Override
	public void setInventorySlotContents(int i, ItemStack itemstack) {
		inv.setInventorySlotContents(i, itemstack);
	}

	@Override
	public int getInventoryStackLimit() {
		return inv.getInventoryStackLimit();
	}

	@Override
	public boolean isUsableByPlayer(EntityPlayer entityplayer) {
		return true;
	}

	@Override
	public void openInventory(EntityPlayer player) {}

	@Override
	public void closeInventory(EntityPlayer player) {}

	@Override
	public boolean isItemValidForSlot(int i, ItemStack itemstack) {
		if (i < 9 && i >= 0) {
			ItemIdentifierStack stack = matrix.getIDStackInSlot(i);
			if (stack != null && !itemstack.isEmpty()) {
				if (isFuzzy() && fuzzyFlags[i].getBitSet().nextSetBit(0) != -1) {
					fuzzyFlags[i] = new DictResource(fuzzyFlags[i], stack);
					return fuzzyFlags[i].matches(ItemIdentifier.get(itemstack));
				}
				return stack.getItem().equalsWithoutNBT(ItemIdentifier.get(itemstack));
			}
		}
		return true;
	}

	@Override
	public int getField(int id) {
		return 0;
	}

	@Override
	public void setField(int id, int value) {

	}

	@Override
	public int getFieldCount() {
		return 0;
	}

	@Override
	public void clear() {

	}

	public void placedBy(EntityLivingBase par5EntityLivingBase) {
		if (par5EntityLivingBase instanceof EntityPlayer) {
			placedBy = PlayerIdentifier.get((EntityPlayer) par5EntityLivingBase);
		}
	}

	public boolean isFuzzy() {
		return world.getBlockState(pos).getValue(LogisticsSolidBlock.metaProperty) == LogisticsSolidBlock.BlockType.LOGISTICS_FUZZYCRAFTING_TABLE;
	}

	@Override
	public CoordinatesGuiProvider getGuiProvider() {
		return NewGuiHandler.getGui(AutoCraftingGui.class).setCraftingTable(this);
	}

	@Override
	public void guiOpenedByPlayer(EntityPlayer player) {
		guiWatcher.add(player);
	}

	@Override
	public void guiClosedByPlayer(EntityPlayer player) {
		guiWatcher.remove(player);
	}

	@Override
	public String getName() {
		return "LogisticsCraftingTable";
	}

	@Override
	public boolean hasCustomName() {
		return true;
	}

	@Override
	public ITextComponent getDisplayName() {
		return null;
	}
}
