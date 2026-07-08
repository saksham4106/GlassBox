import java.util.*;
class Solution {

    public static void main(String[] args) {
        int[] nums = new int[] {1,2,3,4,5,6,7,8,9,10};
        int target = 9;

        int l = 0;
        int r = nums.length -1;
        int mid = (l + r) / 2;
        while(l <= r){
            mid = (l + r) / 2;
            if(nums[mid] == target){
                break;
            }else if(target > nums[mid]){
                l = mid + 1;
            }else{
                r = mid - 1;
            }
        }

        System.out.println(mid);
    }
}